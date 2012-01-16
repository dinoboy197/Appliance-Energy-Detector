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

import info.raack.appliancelabeler.data.Database;
import info.raack.appliancelabeler.data.batch.ItemReader;
import info.raack.appliancelabeler.machinelearning.MachineLearningEngine;
import info.raack.appliancelabeler.machinelearning.MachineLearningEngine.ATTRIBUTE_TYPE;
import info.raack.appliancelabeler.machinelearning.MachineLearningEngine.MODEL_TYPE;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.ApplianceState;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.powerspike.PowerSpike;
import info.raack.appliancelabeler.model.AlgorithmPredictions;
import info.raack.appliancelabeler.model.EnergyTimestep;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.PowerDeltaStateTransition;
import info.raack.appliancelabeler.model.energymonitor.EnergyMonitor;
import info.raack.appliancelabeler.service.DataService.LabelResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

public abstract class BasePowerDrawDetectionAlgorithm<T> extends ApplianceEnergyConsumptionDetectionAlgorithm {
	private Logger logger = LoggerFactory.getLogger(BasePowerDrawDetectionAlgorithm.class);
	
	@Autowired
	protected Database database;
	
	@Autowired
	protected MachineLearningEngine mlEngine;
	
	protected List<ATTRIBUTE_TYPE> attributeTypes;
	private List<String> attributeNames;
	
	@PostConstruct
	public void grabAttributeNamesAndTypes() {
		attributeTypes = new ArrayList<ATTRIBUTE_TYPE>();
		attributeNames = new ArrayList<String>();
		
		// add attribute types from the state transition possibilities
		for(ApplianceStateTransition transitionPrototype : getStateTransitionPrototypes()) {
			attributeTypes.addAll(Arrays.asList(transitionPrototype.getAllAttributeTypes()));
			attributeNames.addAll(Arrays.asList(transitionPrototype.getAllAttributeNames()));
		}
		
		// add appliance id label
		attributeTypes.add(ATTRIBUTE_TYPE.NOMINAL);
		attributeNames.add("user_appliance_id");
	}

	public AlgorithmPredictions algorithmCalculateApplianceEnergyUsePredictions(EnergyMonitor energyMonitor, Queue<EnergyTimestep> originTimesteps, ItemReader<SecondData> dataReader) {

		AlgorithmPredictions algorithmPredictions = new AlgorithmPredictions();
		
		Map<UserAppliance, List<EnergyTimestep>> applianceTimesteps = new HashMap<UserAppliance, List<EnergyTimestep>>();
		
		// get all of the possible user appliances and their last known on/off state
		List<UserAppliance> apps = database.getUserAppliancesForAlgorithmForEnergyMonitor(energyMonitor, getId());
		
		
		Map<UserAppliance, Double> currentTimestepEnergyConsumption = new HashMap<UserAppliance, Double>();
		
		for(UserAppliance appliance : apps) {
			currentTimestepEnergyConsumption.put(appliance, 0d);
			applianceTimesteps.put(appliance, new ArrayList<EnergyTimestep>());
		}

		Map<Long,List<ApplianceStateTransition>> stateTransitions = new HashMap<Long,List<ApplianceStateTransition>>();
		
		if(originTimesteps.size() > 0) {
			// ASSUMPTION - measurements are in chronological order
			if(apps.size() > 0){
				
				// run whatever the energy delta state transition detectors models predict for these new data points
				stateTransitions = detectStateTransitions(database.getAlgorithmResultForMonitorAndAlgorithm(energyMonitor, this), apps.get(0), dataReader);
				
				// reset the data reader
				dataReader.moveToBeginning();
				
				EnergyTimestep currentTimestep = originTimesteps.poll();

				Map<UserAppliance, ApplianceState> applianceStates = new HashMap<UserAppliance, ApplianceState>();
				
				// while we have timesteps remaining
				//logger.debug("Current timestep: " + currentTimestep.getStartTime() + " - " + currentTimestep.getEndTime());
	
				long currentTimestepEndTime = currentTimestep.getEndTime().getTime();
				
				// for each second in the measurement list
				try {
					for(SecondData measurement = dataReader.read(); measurement != null; measurement = dataReader.read()) {
						long currentMeasurementTime = measurement.getCalLong();
						
						while(currentMeasurementTime > currentTimestepEndTime) {
							//logger.debug("End of timestep " + currentTimestep.getEndTime() + "; getting next timestamp");
							
							// get new timestep
							currentTimestep = originTimesteps.poll();
							
							// need to check to see if the current timestep is not null - we won't process up to the very last second, as some will run over the last full 5 minute block
							if(currentTimestep == null) {
								// done!
								break;
							} else {
								currentTimestepEndTime = currentTimestep.getEndTime().getTime();
							}
						}
						
						// update the states of any of the appliances based on any state transitions at this second
						if(stateTransitions.containsKey(currentMeasurementTime)) {
							updateStateForAppliances(applianceStates, stateTransitions.get(currentMeasurementTime), measurement);
						} else {
							updateStateForAppliances(applianceStates, new ArrayList<ApplianceStateTransition>(), measurement);
						}
						
						for(UserAppliance userAppliance : currentTimestepEnergyConsumption.keySet()) {
							// is appliance on?
							if(applianceStates.get(userAppliance) != null && applianceStates.get(userAppliance).isOn() == true) {
								
								ApplianceState applianceState = applianceStates.get(userAppliance);

								double previousConsumption = currentTimestepEnergyConsumption.get(userAppliance);
								
								// BIG ASSUMPTION OF THIS ALGORITHM - appliances all take constant power during their operation = power delta (watts) * 1 second
								double newConsumption = applianceState.getCurrentPower();
								//logger.debug("Appliance " + userAppliance + " last transition was to on; adding " + newConsumption + " watt-seconds to energy consumption");
								
								// add previous consumption plus new consumption
								currentTimestepEnergyConsumption.put(userAppliance, previousConsumption + newConsumption);
							}
						}
						
						if(currentMeasurementTime == currentTimestepEndTime) {
							//logger.debug("Timestep start " + currentTimestep.getStartTime() + "; closing energy measurement");
							// save current energy consumption in this timestep and reset counter
							for(UserAppliance appliance : apps) {
								if(currentTimestepEnergyConsumption.get(appliance) > 0) {
									EnergyTimestep step = currentTimestep.copyWithoutEnergyOrAppliance();
									
									step.setEnergyConsumed(currentTimestepEnergyConsumption.get(appliance));
									step.setUserAppliance(appliance);
									applianceTimesteps.get(appliance).add(step);
								}

								currentTimestepEnergyConsumption.put(appliance, 0d);
							}
							
							// get new timestep
							currentTimestep = originTimesteps.poll();
							
							// need to check to see if the current timestep is not null - we won't process up to the very last second, as some will run over the last full 5 minute block
							if(currentTimestep == null) {
								// done!
								break;
							} else {
								currentTimestepEndTime = currentTimestep.getEndTime().getTime();
							}
						}
					}
				}
				catch (Exception e) {
					throw new RuntimeException("Cannot calculate energy consumption predictions", e);
				}
				logger.debug("Done with energy usage calculations");
			}
		}
		
		List<ApplianceStateTransition> onlyStateTransitions = new ArrayList<ApplianceStateTransition>();
		for(List<? extends ApplianceStateTransition> list : stateTransitions.values()) {
			onlyStateTransitions.addAll(list);
		}
		
		algorithmPredictions.setStateTransitions(onlyStateTransitions);
		algorithmPredictions.setEnergyTimesteps(applianceTimesteps);
		
		return algorithmPredictions;
	}
	
	protected abstract void updateStateForAppliances(Map<UserAppliance, ApplianceState> applianceStates, List<ApplianceStateTransition> currentPowerDeltas, SecondData currentMeasurement);
	
	@Override
	public LabelResult detectAcceptableUserTraining(ItemReader<SecondData> dataReader) {
		logger.debug("Detecting state transitions for user training");
		Map<Long,List<ApplianceStateTransition>> transitions = detectStateTransitionsInternal(null, null, dataReader, -1);
		
		boolean foundIncrease = false;
		boolean foundDecrease = false;
		
		int transitionDelta = 0;
		
		for(long time : transitions.keySet()) {
			for(ApplianceStateTransition transition : transitions.get(time)) {
				if(transition instanceof PowerDeltaStateTransition) {
					PowerDeltaStateTransition powerDelta = (PowerDeltaStateTransition)transition;
					
					if(powerDelta.isOn()) {
						foundIncrease = true;
					} else {
						if(!foundIncrease) {
							// first transition must be a power increase
							return LabelResult.NO_POWER_INCREASE;
						} else {
							foundDecrease = true;
						}
					}
					transitionDelta += powerDelta.getVariableValues()[0];
				}
			}
		}
		
		if(!foundDecrease) {
			if(!foundIncrease) {
				return LabelResult.NO_POWER_INCREASE;
			} else {
				return LabelResult.NO_POWER_DECREASE;
			}
		}
		
		if(Math.abs(transitionDelta) >= 10) {
			return LabelResult.NOT_TURNED_OFF;
		}
		
		return LabelResult.OK;
	}
	
	@Override
	public AlgorithmResult train(EnergyMonitor monitor, ItemReader<SecondData> dataReader) {
		
		logger.debug("Starting training power draw detection algorithm");
		
		// find all state transitions in data based on algorithm - these will not have any labels right now
		Map<Long,List<ApplianceStateTransition>> stateTransitions = detectStateTransitions(null, null, dataReader);
		
		logger.debug("Got all state transitions");
		
		List<ApplianceStateTransition> stateTransitionInstances = new ArrayList<ApplianceStateTransition>();
		
		for(Long key : stateTransitions.keySet()) {
			stateTransitionInstances.addAll(stateTransitions.get(key));
		}
		
		stateTransitions = null;
		
		// gather up any user-contributed labeled transitions and match those against the unlabeled data
		List<ApplianceStateTransition> userLabels = database.getUserOnOffLabels(monitor);
		
		logger.debug("Got all user on off labels");
		
		matchUserLabelsAgainstUnlabeledData(stateTransitionInstances, userLabels);
		
		logger.debug("Labels matched");
		
		
		// now we have labeled and unlabeled data
		
		List<double[]> mlData = new ArrayList<double[]>();
		
		boolean hasLabels = false;
		
		for(ApplianceStateTransition stateTransition : stateTransitionInstances) {
			// only use the state transition peak right now with the user_appliance_id
			
			double[] vals = new double[attributeTypes.size()];
			
			int total = 0;
			
			// add all variables in order
			for(ApplianceStateTransition transitionPrototype : getStateTransitionPrototypes()) {
				if(transitionPrototype.getClass().equals(stateTransition.getClass())) {
					// add the variable values at this point in the full variable list
					for(double value : stateTransition.getVariableValues()) {
						vals[total++] = value;
					}
				} else {
					// no variable values for this section of the full variable list
					for(int i = 0; i < transitionPrototype.getNumberOfVariables(); i++) {
						vals[total++] = missingValue;
					}
				}
			}
			
			// user appliance id
			if(stateTransition.getUserAppliance() != null) {
				hasLabels = true;
				vals[total++] = stateTransition.getUserAppliance().getId();
			} else {
				vals[total++] = missingValue;
			}
			
			mlData.add(vals);
		}
		
		stateTransitionInstances = null;
		
		if(!hasLabels) {
			logger.warn("No labeled data on which to train; not building classifier");
			return null;
		}
		
		logger.debug("About to build machine learning model with " + mlData.size() + " points");
		
		Serializable serializedClassifier = mlEngine.buildModel(MODEL_TYPE.YATSI, attributeTypes, attributeNames, mlData);
		
		logger.debug("Done building ml model");
		
		// return the result
		return serializedClassifier != null ? new AlgorithmResult(monitor, this, serializedClassifier) : null;
	}
	
	protected int getMinuteOfDay(long time) {
		long minutesPerDay = 60 * 24;
		return (int)(time % minutesPerDay);
	}

	protected abstract List<ApplianceStateTransition> getStateTransitionPrototypes();

	private void matchUserLabelsAgainstUnlabeledData(List<ApplianceStateTransition> stateTransitionInstances, List<ApplianceStateTransition> userLabels) {
		logger.debug("Matching user labels against unlabeled state transition data");
		
		Iterator<ApplianceStateTransition> stateTransitionIter = stateTransitionInstances.iterator();
		
		Iterator<ApplianceStateTransition> userLabelIter = userLabels.iterator();
		
		ApplianceStateTransition nextStateTransition = null;
		
		// iterate over all user labels
		while(userLabelIter.hasNext()) {
			ApplianceStateTransition label = userLabelIter.next();
			
			// is this label an on or off?
			if(label.isOn()) {
				// label is on - look forward in time for an off state transition
				
				ApplianceStateTransition onLabel = label;

				// ASSUMPTION - next state transition should be an off
				ApplianceStateTransition offLabel = userLabelIter.next();
				
				long startOnLabel = onLabel.getTime();
				long startOffLabel = offLabel.getTime();
				
				logger.info("User label for " + onLabel.getUserAppliance() + "; " + new Date(onLabel.getTime()) + " - " + new Date(offLabel.getTime()));
				
				// now look for the labels in between here
				
				while(stateTransitionIter.hasNext()) {
					if(nextStateTransition == null) {
						nextStateTransition = stateTransitionIter.next();
					}
					
					long time = nextStateTransition.getTime();
					
					if(startOnLabel <= time && time <= startOffLabel) {
						// this state transition is for this 
						if(nextStateTransition.isOn()) {
							nextStateTransition.setUserAppliance(onLabel.getUserAppliance());
							logger.debug(nextStateTransition + " marked as on");
						} else {
							nextStateTransition.setUserAppliance(offLabel.getUserAppliance());
							logger.debug(nextStateTransition + " marked as off");
						}
					}
					else if(time > startOffLabel) {
						// this next detected state transition is after the end of the user label interval - so it must be for a different set of user labels
						// need to save this state transition for the next possible labeling interval
						logger.debug("Encountered the next state transition after the user off label; skipping to next user label");
						break;
					}
					
					// go to the next state transition
					nextStateTransition = null;
				}
			} else {
				logger.warn("Encountered user labeled point which is an off without a previous on ", label.toString());
			}
		}
	}

	// find all state transitions in the current data block, then predict which appliances generated them based on the previous model
	public Map<Long, List<ApplianceStateTransition>> detectStateTransitions(AlgorithmResult algorithmResult, UserAppliance fallbackAppliance, ItemReader<SecondData> dataReader) {
		int modelId = -1;
		
		if(algorithmResult != null) {
			modelId = mlEngine.loadModel(algorithmResult.getResult());
		} else {
			logger.warn("Cannot load machine learning model, as we had no previous learning result");
		}

		// want sorted map to keep everything in key order when iterating, so use a treemap
		Map<Long,List<ApplianceStateTransition>> transitions = null;
		
		try {
			transitions = detectStateTransitionsInternal(algorithmResult, fallbackAppliance, dataReader, modelId);
			
		} finally {
			if(algorithmResult != null) {
				mlEngine.releaseModel(modelId);
			}
		}
	
		return transitions;
	}
	
	protected Map<Long, List<ApplianceStateTransition>> processOnOffPairs(Map<UserAppliance, List<ApplianceStateTransition>> intermediateTransitions) {
		Map<Long,List<ApplianceStateTransition>> transitions = new TreeMap<Long,List<ApplianceStateTransition>>();
		
		// process the intermediatetransitions list into transitions
		for(UserAppliance appliance : intermediateTransitions.keySet()) {
			
			List<ApplianceStateTransition> applianceTransitions = intermediateTransitions.get(appliance);
			
			processTransitionsIntoMap(transitions, applianceTransitions);
		}
		
		return transitions;
	}

	protected void addTransitionToMap(Map<Long, List<ApplianceStateTransition>> transitions, final ApplianceStateTransition transition) {
		if(transitions.containsKey(transition.getTime())) {
			transitions.get(transition.getTime()).add(transition);
		} else {
			transitions.put(transition.getTime(), new ArrayList<ApplianceStateTransition>() {{ add(transition); }});
		}
	}

	public abstract Map<Long, List<ApplianceStateTransition>> detectStateTransitionsInternal(AlgorithmResult algorithmResult, UserAppliance fallbackAppliance, ItemReader<SecondData> dataReader, int modelId);
	protected abstract void processTransitionsIntoMap(Map<Long, List<ApplianceStateTransition>> transitions, List<ApplianceStateTransition> applianceTransitions);

	
}
