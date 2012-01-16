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
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.frequencyanalysis.FrequencyAnalyzer;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.frequencyanalysis.FrequencyResponseApplianceState;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.frequencyanalysis.FrequencyResponseMoment;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.frequencyanalysis.SlidingWindowFrequencyResponseDetector;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.powerspike.MultiplePowerDeltaApplianceState;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.powerspike.PowerSpike;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.powerspike.SlidingWindowPowerSpikeDetector;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.FrequencyResponseStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.PowerDeltaStateTransition;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

/**
 * The main energy consumption prediction algorithm for iteration 6. This algorithm improves upon iteration 5 by ....
 * 
 * @author traack
 *
 */
@Component
public class FSMPowerSpikeAndFrequencyResponseDetectionAlgorithm extends BasePowerDrawDetectionAlgorithm<List<PowerDeltaStateTransition>> {
	private Logger logger = LoggerFactory.getLogger(FSMPowerSpikeAndFrequencyResponseDetectionAlgorithm.class);
	
	private int stableTime = 5; // number of seconds of power stability before marking a transition
	protected int stabilityThreshold = 10; // amount of change allowed in the power readings during stability checking
	private int differenceThreshold = 50; // threshold (in watts) of power consumption change which results in a new transition event
	
	private List<ApplianceStateTransition> stateTransitionPrototypes;
	
	public FSMPowerSpikeAndFrequencyResponseDetectionAlgorithm() {
		stateTransitionPrototypes = new ArrayList<ApplianceStateTransition>();
		stateTransitionPrototypes.add(new PowerDeltaStateTransition(-1, null, -1, false, 0, 0, 0, 0));
		stateTransitionPrototypes.add(new FrequencyResponseStateTransition(-1, null, -1, false, 0, new double[]{}));
	}
	
	@Autowired
	private FrequencyAnalyzer frequencyAnalyzer;
	
	@Override
	protected List<ApplianceStateTransition> getStateTransitionPrototypes() {
		return stateTransitionPrototypes;
	}
	
	public String getAlgorithmName() {
		return "fsm-power-spike-and-frequency-response-detector";
	}

	public int getId() {
		return 5;
	}
	
	@Override
	public Map<Long, List<ApplianceStateTransition>> detectStateTransitionsInternal(AlgorithmResult algorithmResult, UserAppliance fallbackAppliance, ItemReader<SecondData> dataReader, int modelId) {

		Map<UserAppliance,List<ApplianceStateTransition>> intermediateTransitions = new HashMap<UserAppliance, List<ApplianceStateTransition>>();
		
		// ASSUMPTION - data is already sorted in chronological order
		
		try {
			SlidingWindowPowerSpikeDetector spikeDetector = new SlidingWindowPowerSpikeDetector(stableTime, stabilityThreshold, differenceThreshold);
			SlidingWindowFrequencyResponseDetector frequencyResponseDetector = new SlidingWindowFrequencyResponseDetector(new int[] {15,30,60}, 5, frequencyAnalyzer);
		
			for(SecondData currentPoint = dataReader.read(); currentPoint != null; currentPoint = dataReader.read()) {
				
				PowerSpike spike = spikeDetector.detectCurrentTransition(currentPoint);
				FrequencyResponseMoment moment = frequencyResponseDetector.detectCurrentTransition(currentPoint);
				
				if(spike != null || moment != null) {
					// mark this as a transition
					// use model to predict the appliance id
					UserAppliance userAppliance = null;
					if(algorithmResult != null) {
						double[] attributeValues = new double[attributeTypes.size() - 1];
						
						if(spike != null) {
							attributeValues[0] = Math.abs(spike.getSpike());
							attributeValues[1] = getMinuteOfDay(spike.getDate());
							attributeValues[2] = spike.getTransitionDuration();
						}
						if(moment != null) {
							int beginningVariables = stateTransitionPrototypes.get(0).getNumberOfVariables();
							
							for(int i = beginningVariables; i < attributeTypes.size() - 1; i++) {
								attributeValues[i] = moment.getWavelengthAmplitudes()[i-beginningVariables];
							}
						}
						
						int userApplianceId = mlEngine.predictWithModel(modelId, attributeValues, 0.7);
						if(userApplianceId != -1) {
							
							// get the user appliance that corresponds to this user appliance id
							userAppliance = database.getUserApplianceById(userApplianceId);
							//logger.debug("user appliance " + userApplianceId + " predicted for " + Math.abs(spike.getSpike()) + "; " + userAppliance + " ");
						} else {
							// low confidence in prediction - don't generate a state transition for this
							continue;
						}
					} else {
						userAppliance = fallbackAppliance;
					}
					
						
					List<ApplianceStateTransition> newTransitions = new ArrayList<ApplianceStateTransition>();
					
					if(spike != null) {
						newTransitions.add(new PowerDeltaStateTransition(-1, userAppliance, this.getId(), spike.getSpike() > 0, spike.getDate(), spike.getSpike(), getMinuteOfDay(spike.getDate()), spike.getTransitionDuration()));
					} else {
						newTransitions.add(new FrequencyResponseStateTransition(-1, userAppliance, this.getId(), moment.isOn(), moment.getDate(), moment.getWavelengthAmplitudes()));
					}

					if(intermediateTransitions.containsKey(userAppliance)) {
						intermediateTransitions.get(userAppliance).add(newTransitions.get(0));
					} else {
						intermediateTransitions.put(userAppliance, newTransitions);
					}
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Cannot detect state transitions", e);
		}
		
		return processOnOffPairs(intermediateTransitions);
	}
	

	@Override
	protected void updateStateForAppliances(Map<UserAppliance, ApplianceState> applianceStates, List<ApplianceStateTransition> currentStateTransitions, SecondData currentMeasurement)  {
			// update last appliance transitions
		for(ApplianceStateTransition currentStateTransition : currentStateTransitions) {
			logger.debug("New power delta: " + currentStateTransition);
			if(currentStateTransition.getClass().equals(PowerDeltaStateTransition.class)) {
				ApplianceState applianceState = applianceStates.get(currentStateTransition.getUserAppliance());
				if(applianceState == null) {
					applianceState = new MultiplePowerDeltaApplianceState((PowerDeltaStateTransition)currentStateTransition, differenceThreshold);
					applianceStates.put(currentStateTransition.getUserAppliance(), applianceState);
				}
				else {
					if(applianceState.getClass().equals(MultiplePowerDeltaApplianceState.class)) {
						((MultiplePowerDeltaApplianceState)applianceState).add((PowerDeltaStateTransition)currentStateTransition);
					} else {
						applianceState = new MultiplePowerDeltaApplianceState((PowerDeltaStateTransition)currentStateTransition, differenceThreshold);
						applianceStates.put(currentStateTransition.getUserAppliance(), applianceState);
					}
				}
			} else if(currentStateTransition.getClass().equals(FrequencyResponseStateTransition.class)){
				// only add frequency response state transition if there isn't an existing MultiplePowerDeltaApplianceState in place
				ApplianceState applianceState = applianceStates.get(currentStateTransition.getUserAppliance());
				if(applianceState == null || applianceState instanceof FrequencyResponseStateTransition) {
					applianceStates.put(currentStateTransition.getUserAppliance(), new FrequencyResponseApplianceState((FrequencyResponseStateTransition)currentStateTransition));
				}
			} else {
				throw new RuntimeException("Expected " + PowerDeltaStateTransition.class + " or " + FrequencyResponseStateTransition.class + " but got " + currentStateTransition.getClass());
			}
		}
		
		// check to see if any appliances have been on for at least stableTime seconds
		for(UserAppliance app : applianceStates.keySet()) {
			ApplianceState applianceState = applianceStates.get(app);
			if(applianceState instanceof MultiplePowerDeltaApplianceState) {
				((MultiplePowerDeltaApplianceState)applianceState).addLastMeasurement(currentMeasurement);
			}
			
			if(applianceState.isOn() && (currentMeasurement.getCalLong() - applianceState.getLastTransitionTime()) / 1000 > stableTime * 2) {
				
				boolean doesNotExceed = false;
				
				// check all of the last measurements to see if they are all under the difference threshold
				if(applianceState instanceof MultiplePowerDeltaApplianceState) {
					for(SecondData measurement : ((MultiplePowerDeltaApplianceState)applianceState).getLastMeasurements()) {
						if(applianceState.getCurrentPower() <= measurement.getPower() + differenceThreshold) {
							doesNotExceed = true;
							break;
						}
					}
				} else if(applianceState instanceof FrequencyResponseStateTransition) {
					doesNotExceed = applianceState.getCurrentPower() <= currentMeasurement.getPower() + differenceThreshold;
				}
			
				
				if(!doesNotExceed) {
					logger.debug("At " + new Date(currentMeasurement.getCalLong()) + ", " + app + " did not have an off event yet, but it is consuming more power than the total. Turning all states off manually.");
					
					// tell the appliance state to create a temporary lowering of the energy consumption
					applianceState.createPowerShutoff();
				}
			}
		}
	}

	protected void processTransitionsIntoMap(Map<Long, List<ApplianceStateTransition>> transitions, List<ApplianceStateTransition> applianceTransitions) {
		for(int i = 0; i < applianceTransitions.size(); i++) {
			// add all transitions
			addTransitionToMap(transitions, applianceTransitions.get(i));
		}
	}
	
	protected void addTransitionToMap(Map<Long, List<ApplianceStateTransition>> transitions, final ApplianceStateTransition transition) {
		if(transitions.containsKey(transition.getTime())) {
			transitions.get(transition.getTime()).add(transition);
		} else {
			transitions.put(transition.getTime(), new ArrayList<ApplianceStateTransition>() {{ add(transition); }});
		}
	}
}
