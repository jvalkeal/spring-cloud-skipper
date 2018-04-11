/*
 * Copyright 2017-2018 the original author or authors.
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

import org.springframework.cloud.skipper.SkipperException;
import org.springframework.cloud.skipper.domain.UpgradeRequest;
import org.springframework.cloud.skipper.server.deployer.ReleaseAnalysisReport;
import org.springframework.cloud.skipper.server.deployer.strategies.UpgradeStrategy;
import org.springframework.cloud.skipper.server.service.ReleaseReportService;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperEventHeaders;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperEvents;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperStates;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperVariables;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;

/**
 * StateMachine {@link Action} accepting app with an {@link UpgradeStrategy}.
 *
 * @author Janne Valkealahti
 *
 */
public class UpgradeDeleteSourceAppsAction extends AbstractAction {

	private final ReleaseReportService releaseReportService;
	private final UpgradeStrategy upgradeStrategy;

	/**
	 * Instantiates a new upgrade delete source apps action.
	 *
	 * @param releaseReportService the release report service
	 * @param upgradeStrategy the upgrade strategy
	 */
	public UpgradeDeleteSourceAppsAction(ReleaseReportService releaseReportService, UpgradeStrategy upgradeStrategy) {
		super();
		this.releaseReportService = releaseReportService;
		this.upgradeStrategy = upgradeStrategy;
	}

	@Override
	protected void executeInternal(StateContext<SkipperStates, SkipperEvents> context) {
		ReleaseAnalysisReport releaseAnalysisReport = context.getExtendedState().get(SkipperVariables.RELEASE_ANALYSIS_REPORT,
				ReleaseAnalysisReport.class);

		if (releaseAnalysisReport == null) {
			UpgradeRequest upgradeRequest = context.getExtendedState().get(SkipperEventHeaders.UPGRADE_REQUEST,
					UpgradeRequest.class);
			if (upgradeRequest != null) {
				releaseAnalysisReport = this.releaseReportService.createReport(upgradeRequest, false);
				context.getExtendedState().getVariables().put(SkipperVariables.RELEASE_ANALYSIS_REPORT, releaseAnalysisReport);
			}
		}

		if (releaseAnalysisReport == null) {
			throw new SkipperException("ReleaseAnalysis report is null");
		}

		upgradeStrategy.accept(releaseAnalysisReport.getExistingRelease(), releaseAnalysisReport.getReplacingRelease(),
				releaseAnalysisReport);
	}
}
