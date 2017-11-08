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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.skipper.domain.InstallProperties;
import org.springframework.cloud.skipper.domain.PackageMetadata;
import org.springframework.cloud.skipper.domain.Release;
import org.springframework.cloud.skipper.domain.UpgradeRequest;
import org.springframework.cloud.skipper.server.deployer.ReleaseAnalysisReport;
import org.springframework.cloud.skipper.server.deployer.ReleaseDifference;
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
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperEventHeaders;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperStates;
import org.springframework.cloud.skipper.server.statemachine.StateMachineTests.TestConfig;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.test.StateMachineTestPlan;
import org.springframework.statemachine.test.StateMachineTestPlanBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.ObjectUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;

import java.util.ArrayList;

/**
 * Generic tests for skipper statemachine logic. In these tests we simply
 * want to test machine logic meaning we control actions by using
 * mocks for classes actions are using.
 *
 * @author Janne Valkealahti
 *
 */
@SuppressWarnings("unchecked")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@DirtiesContext
public class StateMachineTests {

	@Autowired
	private ApplicationContext context;

	@MockBean
	private ReleaseManager releaseManager;

	@MockBean
	private PackageService packageService;

	@MockBean
	private ReleaseReportService releaseReportService;

	@MockBean
	private UpgradeStrategy upgradeStrategy;

	@MockBean
	private DeployAppStep deployAppStep;

	@MockBean
	private HealthCheckStep healthCheckStep;

	@MockBean
	private HandleHealthCheckStep handleHealthCheckStep;

	@MockBean
	private ReleaseService releaseService;

	@MockBean
	private ReleaseRepository releaseRepository;

	@SpyBean
	private UpgradeCancelAction upgradeCancelAction;

	@Test
	public void testFactory() {
		StateMachineFactory<SkipperStates, SkipperEvents> factory = context.getBean(StateMachineFactory.class);
		assertThat(factory).isNotNull();
	}

	@Test
	public void testSimpleInstallShouldNotError() throws Exception {
		Mockito.when(packageService.downloadPackage(any()))
				.thenReturn(new org.springframework.cloud.skipper.domain.Package());
		Mockito.when(releaseManager.install(any())).thenReturn(new Release());

		Message<SkipperEvents> message = MessageBuilder
			.withPayload(SkipperEvents.INSTALL)
			.setHeader(SkipperEventHeaders.PACKAGE_METADATA, new PackageMetadata())
			.setHeader(SkipperEventHeaders.INSTALL_PROPERTIES, new InstallProperties())
			.setHeader(SkipperEventHeaders.VERSION, 1)
			.build();

		StateMachineFactory<SkipperStates, SkipperEvents> factory = context.getBean(StateMachineFactory.class);
		StateMachine<SkipperStates, SkipperEvents> stateMachine = factory.getStateMachine("testInstall");

		StateMachineTestPlan<SkipperStates, SkipperEvents> plan =
				StateMachineTestPlanBuilder.<SkipperStates, SkipperEvents>builder()
					.defaultAwaitTime(10)
					.stateMachine(stateMachine)
					.step()
						.expectStateMachineStarted(1)
						.expectStates(SkipperStates.INITIAL)
						.and()
					.step()
						.sendEvent(message)
						.expectStates(SkipperStates.INITIAL)
						.expectStateChanged(4)
						.and()
					.build();
		plan.test();
	}

	@Test
	public void testSimpleUpgradeShouldNotError() throws Exception {

		Mockito.when(releaseReportService.createReport(any()))
				.thenReturn(new ReleaseAnalysisReport(new ArrayList<>(), new ReleaseDifference(true), null, null));

		Mockito.when(upgradeStrategy.checkStatus(any()))
				.thenReturn(true);

		UpgradeRequest upgradeRequest = new UpgradeRequest();

		Message<SkipperEvents> message1 = MessageBuilder
				.withPayload(SkipperEvents.UPGRADE)
				.setHeader(SkipperEventHeaders.UPGRADE_REQUEST, upgradeRequest)
				.build();

		StateMachineFactory<SkipperStates, SkipperEvents> factory = context.getBean(StateMachineFactory.class);
		StateMachine<SkipperStates, SkipperEvents> stateMachine = factory.getStateMachine("testSimpleUpgradeShouldNotError");

		StateMachineTestPlan<SkipperStates, SkipperEvents> plan =
				StateMachineTestPlanBuilder.<SkipperStates, SkipperEvents>builder()
					.defaultAwaitTime(10)
					.stateMachine(stateMachine)
					.step()
						.expectStateMachineStarted(1)
						.expectStates(SkipperStates.INITIAL)
						.and()
					.step()
						.sendEvent(message1)
						.expectStates(SkipperStates.INITIAL)
						.expectStateChanged(9)
						.and()
					.build();
		plan.test();
		Mockito.verify(upgradeCancelAction, never()).execute(any());
	}

	@Test
	public void testUpgradeFailsNewAppFailToDeploy() throws Exception {
		Mockito.when(releaseReportService.createReport(any()))
				.thenReturn(new ReleaseAnalysisReport(new ArrayList<>(), new ReleaseDifference(true), null, null));

		Mockito.when(upgradeStrategy.checkStatus(any()))
				.thenReturn(false);

		UpgradeRequest upgradeRequest = new UpgradeRequest();

		Message<SkipperEvents> message1 = MessageBuilder
				.withPayload(SkipperEvents.UPGRADE)
				.setHeader(SkipperEventHeaders.UPGRADE_REQUEST, upgradeRequest)
				.build();

		StateMachineFactory<SkipperStates, SkipperEvents> factory = context.getBean(StateMachineFactory.class);
		StateMachine<SkipperStates, SkipperEvents> stateMachine = factory.getStateMachine("testUpgradeFailsNewAppFailToDeploy");

		StateMachineTestPlan<SkipperStates, SkipperEvents> plan =
				StateMachineTestPlanBuilder.<SkipperStates, SkipperEvents>builder()
					.defaultAwaitTime(10)
					.stateMachine(stateMachine)
					.step()
						.expectStateMachineStarted(1)
						.expectStates(SkipperStates.INITIAL)
						.and()
					.step()
						.sendEvent(message1)
						.expectStates(SkipperStates.INITIAL)
						.expectStateChanged(9)
						.and()
					.build();
		plan.test();

		Mockito.verify(upgradeCancelAction).execute(any());
	}

	@Test
	public void testRollbackInstall() throws Exception {

		StateMachineFactory<SkipperStates, SkipperEvents> factory = context.getBean(StateMachineFactory.class);
		StateMachine<SkipperStates, SkipperEvents> stateMachine = factory.getStateMachine("testRollbackInstall");

	}


	@Import(StateMachineConfiguration.class)
	static class TestConfig {
	}
}
