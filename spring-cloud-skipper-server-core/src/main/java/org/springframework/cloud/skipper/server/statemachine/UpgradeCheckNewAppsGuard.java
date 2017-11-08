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

import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperEvents;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperStates;
import org.springframework.cloud.skipper.server.statemachine.SkipperStateMachineService.SkipperVariables;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.util.ObjectUtils;

public class UpgradeCheckNewAppsGuard implements Guard<SkipperStates, SkipperEvents> {

	private final boolean upgradeStatus;

	public UpgradeCheckNewAppsGuard(boolean upgradeStatus) {
		this.upgradeStatus = upgradeStatus;
	}

	@Override
	public boolean evaluate(StateContext<SkipperStates, SkipperEvents> context) {
		Boolean status = context.getExtendedState().get(SkipperVariables.UPGRADE_STATUS, Boolean.class);
		if (status == null) {
			return false;
		} else {
			return ObjectUtils.nullSafeEquals(status, upgradeStatus);
		}
	}

}
