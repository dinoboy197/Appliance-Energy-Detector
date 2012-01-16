/*******************************************************************************
 * This file is part of the Appliance Energy Detector, a free household appliance energy disaggregation intelligence engine and webapp.
 * 
 * Copyright (C) 2011,2012 Taylor Raack <traack@raack.info>
 * 
 * The Appliance Energy Detector is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * The Appliance Energy Detector is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License along with the Appliance Energy Detector.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * According to sec. 7 of the GNU Affero General Public License, version 3, the terms of the AGPL are supplemented with the following terms:
 * 
 * If you modify this Program, or any covered work, by linking or combining it with any of the following programs (or modified versions of those libraries), containing parts covered by the terms of those libraries licenses, the licensors of this Program grant you additional permission to convey the resulting work:
 * 
 * Javabeans(TM) Activation Framework 1.1 (activation) - Common Development and Distribution License Version 1.0
 * AspectJ 1.6.9 (aspectjrt and aspectjweaver) - Eclipse Public License 1.0
 * EMMA 2.0.5312 (emma and emma_ant) - Common Public License Version 1.0
 * JAXB Project Libraries 2.2.2 (jaxb-api, jaxb-impl, jaxb-xjc) - Common Development and Distribution License Version 1.0
 * Java Standard Template Library 1.2 (jstl) - Common Development and Distribution License Version 1.0
 * Java Servlet Pages API 2.1 (jsp-api) - Common Development and Distribution License Version 1.0
 * Java Transaction API 1.1 (jta) - Common Development and Distribution License Version 1.0
 * JavaMail(TM) 1.4.1 (mail) - Common Development and Distribution License Version 1.0
 * XML Pull Parser 3 (xpp3) - Indiana University Extreme! Lab Software License Version 1.1.1
 * 
 * The interactive user interface of the software display an attribution notice containing the phrase "Appliance Energy Detector". Interactive user interfaces of unmodified and modified versions must display Appropriate Legal Notices according to sec. 5 of the GNU Affero General Public License, version 3, when you propagate an unmodified or modified version of the Program. In accordance with sec. 7 b) of the GNU Affero General Public License, version 3, these Appropriate Legal Notices must prominently display either a) "Initial Development by <a href='http://www.linkedin.com/in/taylorraack'>Taylor Raack</a>" if displayed in a web browser or b) "Initial Development by Taylor Raack (http://www.linkedin.com/in/taylorraack)" if displayed otherwise.
 ******************************************************************************/
package info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms;

import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.ApplianceState;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.powerspike.SinglePowerDeltaApplianceState;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.PowerDeltaStateTransition;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

/**
 * The main energy consumption prediction algorithm for iteration 5. This algorithm improves upon iteration 4 by allowing for searching for power draw deltas over a number of seconds
 * (rather than just one second) and not generating energy use after a on transition without a corresponding off transition for any appliance.
 * 
 * @author traack
 *
 */
@Component
public class PowerDrawSlidingWindowFlatLoadDetectionAlgorithm extends PowerDrawSlidingWindowDetectionAlgorithm<PowerDeltaStateTransition> {
	private Logger logger = LoggerFactory.getLogger(PowerDrawSlidingWindowFlatLoadDetectionAlgorithm.class);

	public String getAlgorithmName() {
		return "power-delta-sliding-window-on-off-pairs";
	}

	public int getId() {
		return 3;
	}
	
	private List<ApplianceStateTransition> stateTransitionPrototypes;
	
	public PowerDrawSlidingWindowFlatLoadDetectionAlgorithm() {
		stateTransitionPrototypes = new ArrayList<ApplianceStateTransition>();
		stateTransitionPrototypes.add(new PowerDeltaStateTransition(-1, null, -1, false, 0, 0, 0, 0));
	}
	
	@Override
	protected List<ApplianceStateTransition> getStateTransitionPrototypes() {
		return stateTransitionPrototypes;
	}

	@Override
	protected void updateStateForAppliances(Map<UserAppliance, ApplianceState> applianceStates, List<ApplianceStateTransition> currentStateTransitions, SecondData currentMeasurement)  {
		if(currentStateTransitions.size() == 0) {
			return;
		}
			
		// update last appliance transitions
		for(ApplianceStateTransition currentStateTransition : currentStateTransitions) {
			//logger.debug("New power delta: " + currentPowerDelta);
			if(currentStateTransition.getClass().equals(PowerDeltaStateTransition.class)) {
				SinglePowerDeltaApplianceState state = new SinglePowerDeltaApplianceState((PowerDeltaStateTransition)currentStateTransition);
				applianceStates.put(currentStateTransition.getUserAppliance(), state);
			} else {
				throw new RuntimeException("Expected " + PowerDeltaStateTransition.class + " but got " + currentStateTransition.getClass());
			}
		}
		
		// check to see if any appliances have been on for at least stableTime seconds
		for(UserAppliance app : applianceStates.keySet()) {
			ApplianceState state = applianceStates.get(app);
			if(state.isOn() && (currentMeasurement.getCalLong() - state.getLastTransitionTime()) / 1000 > stableTime) {
				// check to see if the total power draw at this second is less than the power draw of the on transition
				if(state.getCurrentPower() > currentMeasurement.getPower()) {
					logger.debug(app + " did not have an off event yet, but it is consuming more power than the total. Turning it off manually.");
					PowerDeltaStateTransition newTransition = new PowerDeltaStateTransition(-1, app, this.getId(), false, currentMeasurement.getCalLong(), -1 * state.getCurrentPower(), getMinuteOfDay(currentMeasurement.getCalLong()), 1);
					SinglePowerDeltaApplianceState newState = new SinglePowerDeltaApplianceState(newTransition);
					applianceStates.put(app, newState);
				}
			}
		}
	}

	@Override
	protected void processTransitionsIntoMap(Map<Long, List<ApplianceStateTransition>> transitions, List<ApplianceStateTransition> applianceTransitions) {
		for(int i = 0; i < applianceTransitions.size(); i++) {
			// if this transition is an off, and the previous transition was an on, add the previous transition and this one
			if(i > 0) {
				PowerDeltaStateTransition current = (PowerDeltaStateTransition)applianceTransitions.get(i);
				PowerDeltaStateTransition previous = (PowerDeltaStateTransition)applianceTransitions.get(i-1);
				
				if(previous.isOn() == true && current.isOn() == false) {
					// add both
					logger.debug("Detected transition pair for " + current.getUserAppliance() + "; on: " + new Date(previous.getTime()) + "; off: " + new Date(current.getTime()));
					addTransitionToMap(transitions, previous);
					addTransitionToMap(transitions, current);
				}
			}
		}
	}

	@Override
	protected double getDetectionProbabilityThreshold() {
		return 0.0;
	}
}
