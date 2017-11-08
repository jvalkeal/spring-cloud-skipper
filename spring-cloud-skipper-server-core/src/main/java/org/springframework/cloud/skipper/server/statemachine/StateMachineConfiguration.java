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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.skipper.server.deployer.ReleaseManager;
import org.springframework.cloud.skipper.server.deployer.strategies.DeployAppStep;
import org.springframework.cloud.skipper.server.deployer.strategies.HandleHealthCheckStep;
import org.springframework.cloud.skipper.server.deployer.strategies.HealthCheckStep;
import org.springframework.cloud.skipper.server.deployer.strategies.UpgradeStrategy;
import org.springframework.cloud.skipper.server.repository.ReleaseRepository;
import org.springframework.cloud.skipper.server.service.PackageService;
import org.springframework.cloud.skipper.server.service.ReleaseReportService;
import org.springframework.cloud.skipper.server.service.ReleaseService;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperEvents;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperStates;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperVariables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;

/**
 * Statemachine(s) related configurations.
 *
 * @author Janne Valkealahti
 *
 */
@Configuration
public class StateMachineConfiguration {

	/**
	 * Configuration defining {@link StateMachineFactory} for skipper release handling.
	 */
	@EnableStateMachineFactory(name = SkipperStateMachineService.STATEMACHINE_FACTORY_BEAN_NAME)
	@Configuration
	public static class SkipperStateMachineFactoryConfig extends StateMachineConfigurerAdapter<SkipperStates, SkipperEvents> {

		private static final Logger log = LoggerFactory
				.getLogger(StateMachineConfiguration.SkipperStateMachineFactoryConfig.class);

		@Autowired
		private ReleaseService releaseService;

		@Autowired
		private ReleaseReportService releaseReportService;

		@Autowired
		private ReleaseRepository releaseRepository;

//		@Autowired
//		private DeployAppStep deployAppStep;
//
//		@Autowired
//		private HealthCheckStep healthCheckStep;
//
//		@Autowired
//		private HandleHealthCheckStep handleHealthCheckStep;

		@Autowired
		private UpgradeStrategy upgradeStrategy;

		@Override
		public void configure(StateMachineStateConfigurer<SkipperStates, SkipperEvents> states) throws Exception {
			states
				.withStates()
					// define main states
					.initial(SkipperStates.INITIAL)
					// clear memory for stored variables
					.stateExit(SkipperStates.INITIAL, resetVariablesAction())
					.state(SkipperStates.INSTALL)
					.state(SkipperStates.DELETE)
					.state(SkipperStates.UPGRADE)
					.state(SkipperStates.ROLLBACK)
					.junction(SkipperStates.ERROR_JUNCTION)
					.and()
					.withStates()
						// substates for install
						.parent(SkipperStates.INSTALL)
						.initial(SkipperStates.INSTALL_PREPARE)
						.stateEntry(SkipperStates.INSTALL_PREPARE, installPrepareAction())
						.state(SkipperStates.INSTALL_INSTALL)
						.stateEntry(SkipperStates.INSTALL_INSTALL, installInstallAction())
						.exit(SkipperStates.INSTALL_EXIT)
						.and()
					.withStates()
						// substates for upgrade
						.parent(SkipperStates.UPGRADE)
						.initial(SkipperStates.UPGRADE_START)
						.stateEntry(SkipperStates.UPGRADE_START, upgradeStartAction())
						.stateEntry(SkipperStates.UPGRADE_DEPLOY_TARGET_APPS, upgradeDeployTargetAppsAction())
						.state(SkipperStates.UPGRADE_WAIT_TARGET_APPS)
						.state(SkipperStates.UPGRADE_CHECK_TARGET_APPS, SkipperEvents.UPGRADE_CANCEL)
						.stateEntry(SkipperStates.UPGRADE_CHECK_TARGET_APPS, upgradeCheckTargetAppsAction())
						.stateEntry(SkipperStates.UPGRADE_DEPLOY_TARGET_APPS_SUCCEED, upgradeDeployTargetAppsSucceedAction())
						.stateEntry(SkipperStates.UPGRADE_DEPLOY_TARGET_APPS_FAILED, upgradeDeployTargetAppsFailedAction())
						.stateEntry(SkipperStates.UPGRADE_CANCEL, upgradeCancelAction())
						.stateEntry(SkipperStates.UPGRADE_DELETE_SOURCE_APPS, upgradeDeleteSourceAppsAction())
						.choice(SkipperStates.UPGRADE_CHECK_CHOICE)
						.exit(SkipperStates.UPGRADE_EXIT)
						.and()
					.withStates()
						// substates for delete
						.parent(SkipperStates.DELETE)
						.initial(SkipperStates.DELETE_DELETE)
						.stateEntry(SkipperStates.DELETE_DELETE, deleteDeleteAction())
						.exit(SkipperStates.DELETE_EXIT)
						.and()
					.withStates()
						// substates for rollback
						.parent(SkipperStates.ROLLBACK)
						.initial(SkipperStates.ROLLBACK_START)
						.stateEntry(SkipperStates.ROLLBACK_START, rollbackStartAction())
						.choice(SkipperStates.ROLLBACK_CHOICE)
						.exit(SkipperStates.ROLLBACK_EXIT_UPGRADE)
						.exit(SkipperStates.ROLLBACK_EXIT_INSTALL)
						.exit(SkipperStates.ROLLBACK_EXIT);
		}

		@Override
		public void configure(StateMachineTransitionConfigurer<SkipperStates, SkipperEvents> transitions) throws Exception {
			transitions

				// transitions around error handling outside of main skipper states
				.withJunction()
					.source(SkipperStates.ERROR_JUNCTION)
					.last(SkipperStates.INITIAL)
					.and()

				// install transitions
				.withExternal()
					.source(SkipperStates.INITIAL).target(SkipperStates.INSTALL)
					.event(SkipperEvents.INSTALL)
					.and()
				.withExternal()
					.source(SkipperStates.INSTALL_PREPARE).target(SkipperStates.INSTALL_INSTALL)
					.and()
				.withExternal()
					.source(SkipperStates.INSTALL_INSTALL).target(SkipperStates.INSTALL_EXIT)
					.and()
				.withExit()
					.source(SkipperStates.INSTALL_EXIT).target(SkipperStates.ERROR_JUNCTION)
					.and()

				// upgrade transitions
				.withExternal()
					.source(SkipperStates.INITIAL).target(SkipperStates.UPGRADE)
					.event(SkipperEvents.UPGRADE)
					.and()
				.withExternal()
					.source(SkipperStates.UPGRADE_START).target(SkipperStates.UPGRADE_DEPLOY_TARGET_APPS)
					.and()
				.withExternal()
					.source(SkipperStates.UPGRADE_DEPLOY_TARGET_APPS).target(SkipperStates.UPGRADE_WAIT_TARGET_APPS)
					.and()
				.withExternal()
					.source(SkipperStates.UPGRADE_WAIT_TARGET_APPS).target(SkipperStates.UPGRADE_CHECK_CHOICE)
					.timer(1000)
					.and()
				.withExternal()
					.source(SkipperStates.UPGRADE_CHECK_TARGET_APPS).target(SkipperStates.UPGRADE_WAIT_TARGET_APPS)
					.and()
				.withExternal()
					// define transition which allows to break out from wait/check loop
					// if machine is in state where this can happen
					.source(SkipperStates.UPGRADE_WAIT_TARGET_APPS).target(SkipperStates.UPGRADE_CANCEL)
					.event(SkipperEvents.UPGRADE_CANCEL)
					.and()
				.withChoice()
					.source(SkipperStates.UPGRADE_CHECK_CHOICE)
					.first(SkipperStates.UPGRADE_DEPLOY_TARGET_APPS_SUCCEED, upgradeOkGuard())
					.then(SkipperStates.UPGRADE_DEPLOY_TARGET_APPS_FAILED, upgradeFailedGuard())
					.last(SkipperStates.UPGRADE_CHECK_TARGET_APPS)
					.and()
				.withExternal()
					.source(SkipperStates.UPGRADE_DEPLOY_TARGET_APPS_SUCCEED).target(SkipperStates.UPGRADE_DELETE_SOURCE_APPS)
					.event(SkipperEvents.UPGRADE_ACCEPT)
					.and()
				.withExternal()
					.source(SkipperStates.UPGRADE_DEPLOY_TARGET_APPS_SUCCEED).target(SkipperStates.UPGRADE_CANCEL)
					.event(SkipperEvents.UPGRADE_CANCEL)
					.and()
				.withExternal()
					.source(SkipperStates.UPGRADE_DEPLOY_TARGET_APPS_FAILED).target(SkipperStates.UPGRADE_CANCEL)
					.and()
				.withExternal()
					.source(SkipperStates.UPGRADE_CANCEL).target(SkipperStates.UPGRADE_EXIT)
					.and()
				.withExternal()
					.source(SkipperStates.UPGRADE_DELETE_SOURCE_APPS).target(SkipperStates.UPGRADE_EXIT)
					.and()
				.withExit()
					.source(SkipperStates.UPGRADE_EXIT).target(SkipperStates.ERROR_JUNCTION)
					.and()

				// delete transitions
				.withExternal()
					.source(SkipperStates.INITIAL).target(SkipperStates.DELETE)
					.event(SkipperEvents.DELETE)
					.and()
				.withExternal()
					.source(SkipperStates.DELETE_DELETE).target(SkipperStates.DELETE_EXIT)
					.and()
				.withExit()
					.source(SkipperStates.DELETE_EXIT).target(SkipperStates.ERROR_JUNCTION)
					.and()

				// rollback transitions
				.withExternal()
					.source(SkipperStates.INITIAL).target(SkipperStates.ROLLBACK)
					.event(SkipperEvents.ROLLBACK)
					.and()
				.withExternal()
					.source(SkipperStates.ROLLBACK_START).target(SkipperStates.ROLLBACK_CHOICE)
					.and()
				.withChoice()
					.source(SkipperStates.ROLLBACK_CHOICE)
					.first(SkipperStates.ROLLBACK_EXIT_UPGRADE, rollbackUpgradeGuard())
					.then(SkipperStates.ROLLBACK_EXIT_INSTALL, rollbackInstallGuard())
					.last(SkipperStates.ROLLBACK_EXIT)
					.and()
				.withExit()
					.source(SkipperStates.ROLLBACK_EXIT).target(SkipperStates.ERROR_JUNCTION)
					.and()
				.withExit()
					.source(SkipperStates.ROLLBACK_EXIT_UPGRADE).target(SkipperStates.UPGRADE)
					.and()
				.withExit()
					.source(SkipperStates.ROLLBACK_EXIT_INSTALL).target(SkipperStates.INSTALL);
		}

		@Bean
		public ResetVariablesAction resetVariablesAction() {
			return new ResetVariablesAction();
		}

		@Bean
		public Guard<SkipperStates, SkipperEvents> errorGuard() {
			return context -> context.getExtendedState().getVariables().containsKey(SkipperVariables.ERROR);
		}

		@Bean
		public InstallInstallAction installInstallAction() {
			return new InstallInstallAction(releaseService);
		}

		@Bean
		public InstallPrepareAction installPrepareAction() {
			return new InstallPrepareAction();
		}

		@Bean
		public UpgradeStartAction upgradeStartAction() {
			return new UpgradeStartAction(releaseReportService, releaseService);
		}

		@Bean
		public UpgradeDeployTargetAppsAction upgradeDeployTargetAppsAction() {
			return new UpgradeDeployTargetAppsAction(upgradeStrategy);
		}

		@Bean
		public UpgradeCheckTargetAppsAction upgradeCheckTargetAppsAction() {
			return new UpgradeCheckTargetAppsAction(upgradeStrategy);
		}

		@Bean
		public UpgradeCheckNewAppsGuard upgradeOkGuard() {
			return new UpgradeCheckNewAppsGuard(true);
		}

		@Bean
		public UpgradeCheckNewAppsGuard upgradeFailedGuard() {
			return new UpgradeCheckNewAppsGuard(false);
		}

		@Bean
		public UpgradeDeployTargetAppsSucceedAction upgradeDeployTargetAppsSucceedAction() {
			return new UpgradeDeployTargetAppsSucceedAction();
		}

		@Bean
		public UpgradeDeployTargetAppsFailedAction upgradeDeployTargetAppsFailedAction() {
			return new UpgradeDeployTargetAppsFailedAction();
		}

		@Bean
		public UpgradeCancelAction upgradeCancelAction() {
			return new UpgradeCancelAction(upgradeStrategy);
		}

		@Bean
		public UpgradeDeleteSourceAppsAction upgradeDeleteSourceAppsAction() {
			return new UpgradeDeleteSourceAppsAction(upgradeStrategy);
		}

		@Bean
		public DeleteDeleteAction deleteDeleteAction() {
			return new DeleteDeleteAction(releaseService);
		}

		@Bean
		public RollbackStartAction rollbackStartAction() {
			return new RollbackStartAction(releaseService, releaseRepository);
		}

		@Bean
		public Guard<SkipperStates, SkipperEvents> rollbackInstallGuard() {
			return context -> {
				log.info("XXXXXXXXXXXXXXXX 31 ");
				boolean xxx = context.getExtendedState().getVariables().containsKey(SkipperVariables.TARGET_RELEASE)
				&& !context.getExtendedState().getVariables().containsKey(SkipperVariables.SOURCE_RELEASE);
				log.info("XXXXXXXXXXXXXXXX 32 {}", xxx);
				return xxx;
//				return context.getExtendedState().getVariables().containsKey(SkipperVariables.TARGET_RELEASE)
//						&& !context.getExtendedState().getVariables().containsKey(SkipperVariables.SOURCE_RELEASE);
			};
		}

		@Bean
		public Guard<SkipperStates, SkipperEvents> rollbackUpgradeGuard() {
			return context -> {
				log.info("XXXXXXXXXXXXXXXX 21 ");
				boolean xxx = context.getExtendedState().getVariables().containsKey(SkipperVariables.TARGET_RELEASE)
				&& context.getExtendedState().getVariables().containsKey(SkipperVariables.SOURCE_RELEASE);
				log.info("XXXXXXXXXXXXXXXX 22 {}", xxx);
				return xxx;
//				return context.getExtendedState().getVariables().containsKey(SkipperVariables.TARGET_RELEASE)
//						&& context.getExtendedState().getVariables().containsKey(SkipperVariables.SOURCE_RELEASE);
			};
		}
	}

	/**
	 * Configuration related to {@link SkipperStateMachineService}.
	 */
	@Configuration
	public static class StateMachineServiceConfig {

		@Bean
		public SkipperStateMachineService stateMachineService(StateMachineFactory<SkipperStates, SkipperEvents> skipperStateMachineFactory) {
			return new SkipperStateMachineService(skipperStateMachineFactory);
		}
	}

}
