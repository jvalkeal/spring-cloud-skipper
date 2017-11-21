/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.skipper.server.statemachine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.skipper.SkipperException;
import org.springframework.cloud.skipper.domain.InstallProperties;
import org.springframework.cloud.skipper.domain.InstallRequest;
import org.springframework.cloud.skipper.domain.Release;
import org.springframework.cloud.skipper.domain.UpgradeRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateContext.Stage;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.service.StateMachineService;
import org.springframework.util.Assert;
import org.springframework.util.concurrent.SettableListenableFuture;

/**
 * Service class for state machine hiding its operational logic.
 *
 * @author Janne Valkealahti
 *
 */
public class SkipperStateMachineService {

	public final static String STATEMACHINE_FACTORY_BEAN_NAME = "skipperStateMachineFactory";
	private static final Logger log = LoggerFactory.getLogger(SkipperStateMachineService.class);
	private final StateMachineService<SkipperStates, SkipperEvents> stateMachineService;

	/**
	 * Instantiates a new skipper state machine service.
	 *
	 * @param stateMachineService the state machine service
	 */
	public SkipperStateMachineService(StateMachineService<SkipperStates, SkipperEvents> stateMachineService) {
		Assert.notNull(stateMachineService, "'stateMachineService' must be set");
		this.stateMachineService = stateMachineService;
	}

	public Release installRelease(InstallRequest installRequest) {
		return installReleaseInternal(installRequest, null, null);
	}

	public Release installRelease(Long id, InstallProperties installProperties) {
		return installReleaseInternal(null, id, installProperties);
	}

	private Release installReleaseInternal(InstallRequest installRequest, Long id, InstallProperties installProperties) {
		String releaseName = installRequest != null ? installRequest.getInstallProperties().getReleaseName() : installProperties.getReleaseName();
		Message<SkipperEvents> message = MessageBuilder
				.withPayload(SkipperEvents.INSTALL)
				.setHeader(SkipperEventHeaders.INSTALL_REQUEST, installRequest)
				.setHeader(SkipperEventHeaders.INSTALL_ID, id)
				.setHeader(SkipperEventHeaders.INSTALL_PROPERTIES, installProperties)
				.build();
		return handleMessageAndWait(message, releaseName);
	}

	public Release upgradeRelease(UpgradeRequest upgradeRequest) {
		String releaseName = upgradeRequest.getUpgradeProperties().getReleaseName();
		Message<SkipperEvents> message = MessageBuilder
				.withPayload(SkipperEvents.UPGRADE)
				.setHeader(SkipperEventHeaders.UPGRADE_REQUEST, upgradeRequest)
				.build();
		return handleMessageAndWait(message, releaseName);
	}

	public Release deleteRelease(String releaseName) {
		Message<SkipperEvents> message = MessageBuilder
				.withPayload(SkipperEvents.DELETE)
				.setHeader(SkipperEventHeaders.RELEASE_NAME, releaseName)
				.build();
		return handleMessageAndWait(message, releaseName);
	}

	public Release rollbackRelease(final String releaseName, final int rollbackVersion) {
		Message<SkipperEvents> message = MessageBuilder
				.withPayload(SkipperEvents.ROLLBACK)
				.setHeader(SkipperEventHeaders.RELEASE_NAME, releaseName)
				.setHeader(SkipperEventHeaders.ROLLBACK_VERSION, rollbackVersion)
				.build();
		return handleMessageAndWait(message, releaseName);
	}

	private Release handleMessageAndWait(Message<SkipperEvents> message, String machineId) {
		StateMachine<SkipperStates, SkipperEvents> stateMachine = stateMachineService.acquireStateMachine(machineId);
		stateMachine.start();
		SettableListenableFuture<Release> future = new SettableListenableFuture<>();

		StateMachineListener<SkipperStates, SkipperEvents> listener = new StateMachineListenerAdapter<SkipperStates, SkipperEvents>() {

			@Override
			public void stateContext(StateContext<SkipperStates, SkipperEvents> stateContext) {
				if (stateContext.getStage() == Stage.STATE_ENTRY && stateContext.getTarget().getId().equals(SkipperStates.INITIAL)) {
					Release release = (Release) stateContext.getExtendedState().getVariables().get(SkipperVariables.RELEASE);
					log.debug("setting future {}", release);
					future.set(release);
				}
			}
		};

		stateMachine.addStateListener(listener);

		future.addCallback(result -> {
			stateMachine.removeStateListener(listener);
		}, throwable -> {
			stateMachine.removeStateListener(listener);
		});

		if (stateMachine.sendEvent(message)) {
			try {
				return future.get();
			}
			catch (Exception e) {
				throw new SkipperException("Error waiting to get Release from a statemachine", e);
			}
		}
		else {
			throw new SkipperException("Statemachine is not in state ready to " + message.getPayload());
		}
	}

	/**
	 * Enumeration of all possible states used by a machine.
	 */
	public enum SkipperStates {

		/**
		 * Initial state of a machine where instantiated machine goes.
		 */
		INITIAL,

		/**
		 * Central error handling state.
		 */
		ERROR,

		/**
		 * Central junction where all transitions from main skipper states terminates.
		 */
		ERROR_JUNCTION,

		/**
		 * Parent state of all install related states.
		 */
		INSTALL,

		/**
		 * Initial state where all init logic happens before we can go
		 * to other states.
		 */
		INSTALL_PREPARE,

		/**
		 * State where apps deployment happens.
		 */
		INSTALL_INSTALL,

		/**
		 * Pseudostate used as a controlled exit point from {@link #INSTALL}.
		 */
		INSTALL_EXIT,

		/**
		 * Parent state of all upgrade related states.
		 */
		UPGRADE,

		/**
		 * State where all init logic happens before we can go
		 * to state where actual new apps will be deployed.
		 */
		UPGRADE_START,

		/**
		 * State where new apps are getting deployed.
		 */
		UPGRADE_DEPLOY_TARGET_APPS,

		/**
		 * Intermediate state where machine pauses to either doing
		 * a loop via {@link #UPGRADE_CHECK_NEW_APPS} back to itself
		 * or hopping into {@link #UPGRADE_CANCEL}.
		 */
		UPGRADE_WAIT_TARGET_APPS,

		/**
		 * State where status of a target release is checked.
		 */
		UPGRADE_CHECK_TARGET_APPS,

		/**
		 * State where machine ends up if target release is considered failed.
		 */
		UPGRADE_DEPLOY_TARGET_APPS_FAILED,

		/**
		 * State where machine ends up if target release is considered successful.
		 */
		UPGRADE_DEPLOY_TARGET_APPS_SUCCEED,

		/**
		 * State where machine goes if it is possible to cancel current
		 * upgrade operation.
		 */
		UPGRADE_CANCEL,

		/**
		 * State where source apps are getting deleted.
		 */
		UPGRADE_DELETE_SOURCE_APPS,

		/**
		 * Pseudostate used to chooce between {@link #UPGRADE_DELETE_SOURCE_APPS}
		 * and {@link #UPGRADE_CHECK_NEW_APPS}
		 */
		UPGRADE_CHECK_CHOICE,

		/**
		 * Pseudostate used as a controlled exit point from {@link #UPGRADE}.
		 */
		UPGRADE_EXIT,

		/**
		 * Parent state of all delete related states.
		 */
		DELETE,

		/**
		 * State where release delete happens.
		 */
		DELETE_DELETE,

		/**
		 * Pseudostate used as a controlled exit point from {@link #DELETE}.
		 */
		DELETE_EXIT,

		/**
		 * Parent state of all rollback related states.
		 */
		ROLLBACK,

		/**
		 * Initialisation state where future branch from {@link #ROLLBACK_CHOICE}
		 * is desided.
		 */
		ROLLBACK_START,

		/**
		 * Pseudostate makind decision between exit points {@link #ROLLBACK_EXIT},
		 * {@link #ROLLBACK_EXIT_INSTALL} and {@link #ROLLBACK_EXIT_UPGRADE}.
		 */
		ROLLBACK_CHOICE,

		/**
		 * Controlled exit into {@link #INSTALL}.
		 */
		ROLLBACK_EXIT_INSTALL,

		/**
		 * Controlled exit into {@link #UPGRADE}.
		 */
		ROLLBACK_EXIT_UPGRADE,

		/**
		 * Controlled exit which acts as a fallback in case either {@link #ROLLBACK_EXIT_INSTALL}
		 * or {@link #ROLLBACK_EXIT_UPGRADE} cannot be chosen for some reason.
		 */
		ROLLBACK_EXIT;
	}

	/**
	 * Enumeration of all possible events used by a machine.
	 */
	public enum SkipperEvents {

		/**
		 * Main level event instructing an install request.
		 */
		INSTALL,

		/**
		 * Main level event instructing a delete request.
		 */
		DELETE,

		/**
		 * Main level event instructing an upgrade request.
		 */
		UPGRADE,

		/**
		 * While being on {@link States#UPGRADE}, this event can be used
		 * to try upgrade cancel operation. Cancellation happens if machine
		 * is in a state where it is possible to go into cancel procedure.
		 */
		UPGRADE_CANCEL,

		/**
		 * While being on {@link States#UPGRADE}, this event can be used
		 * to try upgrade accept procedure.
		 */
		UPGRADE_ACCEPT,

		/**
		 * Main level event instructing a rollback request.
		 */
		ROLLBACK;
	}

	/**
	 * Definitions of possible event headers used by a machine. Defined as
	 * string constants instead of enums because spring message headers
	 * don't work with enums.
	 */
	public final class SkipperEventHeaders {
		public static final String SOURCE_RELEASE = "SOURCE_RELEASE";
		public static final String TARGET_RELEASE = "TARGET_RELEASE";
		public static final String PACKAGE_METADATA = "PACKAGE_METADATA";
		public static final String VERSION = "VERSION";
		public static final String INSTALL_ID = "INSTALL_ID";
		public static final String INSTALL_PROPERTIES = "INSTALL_PROPERTIES";
		public static final String INSTALL_REQUEST = "INSTALL_REQUEST";
		public static final String UPGRADE_REQUEST = "UPGRADE_REQUEST";
		public static final String UPGRADE_TIMEOUT = "UPGRADE_TIMEOUT";
		public static final String RELEASE_NAME = "RELEASE_NAME";
		public static final String ROLLBACK_VERSION = "ROLLBACK_VERSION";
	}

	/**
	 * Extended state variable names for skipper statemachine.
	 */
	public enum SkipperVariables {

		/**
		 * Global error variable which any component can set to
		 * indicate unprocessed exception.
		 */
		ERROR,

		RELEASE, OPERATION, RELEASE_ANALYSIS_REPORT,

		SOURCE_RELEASE, TARGET_RELEASE,

		UPGRADE_CUTOFF_TIME,

		UPGRADE_STATUS;
	}

}
