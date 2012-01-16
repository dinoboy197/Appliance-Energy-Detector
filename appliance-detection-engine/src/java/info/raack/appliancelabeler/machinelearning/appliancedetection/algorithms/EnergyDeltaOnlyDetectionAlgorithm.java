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

import info.raack.appliancelabeler.data.batch.ItemReader;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.ApplianceState;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.powerspike.SinglePowerDeltaApplianceState;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.PowerDeltaStateTransition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

@Component
public class EnergyDeltaOnlyDetectionAlgorithm extends BasePowerDrawDetectionAlgorithm<PowerDeltaStateTransition> {
	
	private Logger logger = LoggerFactory.getLogger(EnergyDeltaOnlyDetectionAlgorithm.class);
	
	// window size is 1 second to check for power consumption changes
	private int differenceThreshold = 50; // threshold (in watts) of power consumption change which results in a new transition event
	
	private List<ApplianceStateTransition> stateTransitionPrototypes;
	
	public EnergyDeltaOnlyDetectionAlgorithm() {
		stateTransitionPrototypes = new ArrayList<ApplianceStateTransition>();
		stateTransitionPrototypes.add(new PowerDeltaStateTransition(-1, null, -1, false, 0, 0, 0, 0));
	}
	
	@Override
	protected List<ApplianceStateTransition> getStateTransitionPrototypes() {
		return stateTransitionPrototypes;
	}
	
	public Map<Long, List<ApplianceStateTransition>> detectStateTransitionsInternal(AlgorithmResult algorithmResult, UserAppliance fallbackAppliance, ItemReader<SecondData> dataReader, int modelId) {
		SecondData firstPoint = null;
		SecondData secondPoint = null;
		Map<Long,List<ApplianceStateTransition>> transitions = null;
		
		// ASSUMPTION - data is already sorted in chronological order
		try {
			transitions = new TreeMap<Long,List<ApplianceStateTransition>>();
			for(firstPoint = dataReader.read(), secondPoint = dataReader.read(); secondPoint != null; firstPoint = secondPoint, secondPoint = dataReader.read()) {
				int diff = secondPoint.getPower() - firstPoint.getPower();
				if(Math.abs(diff) >= differenceThreshold) {

					int minuteOfDay = getMinuteOfDay(firstPoint.getCalLong());
					
					// use model to predict the appliance id
					UserAppliance userAppliance = null;
					if(algorithmResult != null) {
						int userApplianceId = mlEngine.predictWithModel(modelId, new double[] {Math.abs(diff), minuteOfDay, 1}, 0);
						if(userApplianceId != -1) {
							
							// get the user appliance that corresponds to this user appliance id
							userAppliance = database.getUserApplianceById(userApplianceId);
							logger.debug("user appliance " + userApplianceId + " predicted for " + Math.abs(diff) + "; " + userAppliance + " ");
						} else {
							userAppliance = fallbackAppliance;
						}
					} else {
						userAppliance = fallbackAppliance;
					}
					
					
					final PowerDeltaStateTransition newTransition = new PowerDeltaStateTransition(-1, userAppliance, this.getId(), diff > 0, firstPoint.getCalLong(), diff, minuteOfDay, 1);
					

					logger.debug("Detected new power state transition " + newTransition);
					if(transitions.containsKey(firstPoint.getCalLong())) {
						transitions.get(firstPoint.getCalLong()).add(newTransition);
					} else {
						transitions.put(firstPoint.getCalLong(), new ArrayList<ApplianceStateTransition>() {{ add(newTransition); }});
					}
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Cannot detect state transitions", e);
		}
		return transitions;
	}
	
	public String getAlgorithmName() {
		return "energy-delta-only";
	}

	public int getId() {
		return 2;
	}

	@Override
	protected void updateStateForAppliances(Map<UserAppliance, ApplianceState> applianceStates, List<ApplianceStateTransition> currentStateTransitions, SecondData currentMeasurement)  {
		// update last appliance transitions
		for(ApplianceStateTransition currentStateTransition : currentStateTransitions) {
			if(currentStateTransition.getClass().equals(PowerDeltaStateTransition.class)) {
				//logger.debug("New power delta: " + currentPowerDelta);
				SinglePowerDeltaApplianceState state = new SinglePowerDeltaApplianceState((PowerDeltaStateTransition)currentStateTransition);
				applianceStates.put(currentStateTransition.getUserAppliance(), state);
			} else {
				throw new RuntimeException("Expected " + PowerDeltaStateTransition.class + " but got " + currentStateTransition.getClass());
			}
		}
	}

	@Override
	protected Map<Long, List<ApplianceStateTransition>> processOnOffPairs(Map<UserAppliance, List<ApplianceStateTransition>> intermediateTransitions) {
		Map<Long,List<ApplianceStateTransition>> transitions = new TreeMap<Long,List<ApplianceStateTransition>>();
		
		// process the intermediatetransitions list into transitions
		for(UserAppliance appliance : intermediateTransitions.keySet()) {
			
			List<ApplianceStateTransition> applianceTransitions = intermediateTransitions.get(appliance);
			
			processTransitionsIntoMap(transitions, applianceTransitions);
		}
		
		return transitions;
	}

	@Override
	protected void processTransitionsIntoMap(Map<Long, List<ApplianceStateTransition>> transitions, List<ApplianceStateTransition> applianceTransitions) {
		// stub method - does not need to do anything for this detection algorithm
	}

	
}
