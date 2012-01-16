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
package info.raack.appliancedetection.evaluation.model;

import info.raack.appliancedetection.evaluation.model.appliance.SimulatedAppliance;
import info.raack.appliancelabeler.model.EnergyTimestep;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.GenericStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.SimulatedStateTransition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Evaluation {
	private Logger logger = LoggerFactory.getLogger(Evaluation.class);
	
	protected Simulation simulation;
	
	protected Map<UserAppliance, List<EnergyTimestep>> predictedEnergyUsage;
	
	protected long overallEnergyError = 0;
	protected double overallAccuracy = 0;
	protected double stateTransitionAccuracy = 0;
	protected double stateTransitionRecall = 0;
	protected double stateTransitionPrecision = 0;
	protected Map<UserAppliance, Long> applianceEnergyErrors = new HashMap<UserAppliance, Long>();
	protected Map<UserAppliance, List<ApplianceStateTransition>> predictedApplianceStateTransitions;
	
	protected Evaluation() {
		
	}
	
	public Evaluation(Simulation simulation, Map<UserAppliance, List<EnergyTimestep>> predictedEnergyUsage, Map<UserAppliance, List<ApplianceStateTransition>> predictedApplianceStateTransitions) {
		this.simulation = simulation;
		this.predictedEnergyUsage = predictedEnergyUsage;
		this.predictedApplianceStateTransitions = predictedApplianceStateTransitions;
		
		calculateErrors();
		
		calculateStateTransitionAccuracy();
	}
	
	private void calculateStateTransitionAccuracy() {
		// loop through all state transitions for both predicted and true (simulated) moving forwards in time

		// if two state transitions for the same appliance and in the same increase / decrease state occur within 10 seconds of each other, then they are a match
		// if a predicted transition doesn't have a true transition match, then it's a false positive
		// if a true transition match doesn't have a predicted transition match, then it's a false negative

		LinkedList<ApplianceStateTransition> allTransitions = new LinkedList<ApplianceStateTransition>();
		
		for(List<ApplianceStateTransition> transitions : predictedApplianceStateTransitions.values()) {
			allTransitions.addAll(transitions);
		}
		for(SimulatedAppliance appliance : simulation.getSimulatedAppliances()) {
			allTransitions.addAll(appliance.getAllApplianceStateTransitions());
		}
		
		// put in sorted order
		Collections.sort(allTransitions, new Comparator<ApplianceStateTransition>() {
			public int compare(ApplianceStateTransition one, ApplianceStateTransition two) {
				return (int)(one.getTime() - two.getTime());
			}});
		
		if(allTransitions.size() == 0) {
			stateTransitionAccuracy = 100;
			return;
		}
		
		long fp = 0;
		long fn = 0;
		long tp = 0;
		long tn = 0;

		int matchTimeDelay = 10;
		LinkedList<List<ApplianceStateTransition>> pastTransitions = new LinkedList<List<ApplianceStateTransition>>();


		long currentTime = simulation.getStartTime().getTime();
		
		while(currentTime < simulation.getEndTime().getTime()) {
			List<ApplianceStateTransition> currentTransitions = new ArrayList<ApplianceStateTransition>();

			// expire transitions matchTimeDelay seconds old
			if(pastTransitions.size() >= matchTimeDelay) {
				List<ApplianceStateTransition> transitions = pastTransitions.get(0);

				for(ApplianceStateTransition transition : transitions) {
					logger.debug("Missed match of " + transition.getClass().getSimpleName() + ": " + transition);
					
					if(transition instanceof SimulatedStateTransition) {
						fn++;
					}
					else {
						fp++;
					}
				}
				
				// now remove this transition list
				pastTransitions.pop();
			}


			// get
			while(allTransitions.size() > 0) {
				ApplianceStateTransition transition = allTransitions.pop();
				if(transition.getTime() == currentTime) {
					// this transition belongs in this second
					currentTransitions.add(transition);
				} else {
					// this transition does not belong in this second - put it back on the deque
					allTransitions.push(transition);
					break;
				}
			}
			
			// create a new transitions map
			List<ApplianceStateTransition> newTransitions = new ArrayList<ApplianceStateTransition>();

			pastTransitions.add(newTransitions);
			
			for(ApplianceStateTransition newTransition : currentTransitions) {
				// look for a matching transition in the past

				boolean found = false;
				logger.debug("Found new transition " + newTransition);
				
				for(List<ApplianceStateTransition> pastTransitionsAtSecond : pastTransitions) {
					ApplianceStateTransition toRemove = null;
					
					
					for(ApplianceStateTransition potentialTransition : pastTransitionsAtSecond) {
						if(!(potentialTransition instanceof SimulatedStateTransition) && newTransition instanceof SimulatedStateTransition) {
							logger.debug("Attemping to match predicted transition at " + new Date(potentialTransition.getTime()) + " going " + (potentialTransition.isOn() ? "on " : "off ") + " to simulated transition at " + new Date(newTransition.getTime()) + " going " + (newTransition.isOn() ? "on " : "off ") + " for " + newTransition.getUserAppliance());
							
							if(((SimulatedAppliance)newTransition.getUserAppliance()).getLabeledAppliance().equals(potentialTransition.getUserAppliance()) && newTransition.isOn() == potentialTransition.isOn()) {
								toRemove = potentialTransition;
								logger.debug("Matched predicted transition at " + new Date(potentialTransition.getTime()) + " to simulated transition at " + new Date(newTransition.getTime()) + " going " + (newTransition.isOn() ? "on " : "off ") + " for " + potentialTransition.getUserAppliance());
								found = true;
								
								break;
							}
						} else if(potentialTransition instanceof SimulatedStateTransition && !(newTransition instanceof SimulatedStateTransition)) {
							logger.debug("Attemping to match predicted transition at " + new Date(newTransition.getTime()) + " going " + (newTransition.isOn() ? "on " : "off ") + " to simulated transition at " + new Date(potentialTransition.getTime()) + " going " + (potentialTransition.isOn() ? "on " : "off ") + " for " + potentialTransition.getUserAppliance());
							
							if(((SimulatedAppliance)potentialTransition.getUserAppliance()).getLabeledAppliance().equals(newTransition.getUserAppliance()) && newTransition.isOn() == potentialTransition.isOn()) {
								toRemove = potentialTransition;
								logger.debug("Matched predicted transition at " + new Date(newTransition.getTime()) + " to simulated transition at " + new Date(potentialTransition.getTime()) + " going " + (newTransition.isOn() ? "on " : "off ") + " for " + newTransition.getUserAppliance());
								found = true;
								break;
							}
						}
						
						
					}
					
					pastTransitionsAtSecond.remove(toRemove);
					
					if(found) {
						// don't need to continue if we found a matching transition
						tp++;
						break;
					}
				}

				if(!found) {
					newTransitions.add(newTransition);
				}
			}
			
			if(currentTransitions.size() == 0) {
				//tn++;
			}
			
			
			// increment current time by one second
			currentTime += 1000;
			
		}

		stateTransitionAccuracy = 100.0f * (double)(tp + tn) / (double)(tp + fp + fn + tn);
		if(tp + fp + fn + tn == 0) {
			stateTransitionAccuracy = 100;
		}
		stateTransitionRecall = 100.0f * (double)tp / (double)(tp + fp);
		if(tp + fp == 0) {
			stateTransitionRecall = 100;
		}
		stateTransitionPrecision = 100.0f * (double)tp / (double)(tp + fn);
		if(tp + fn == 0) {
			stateTransitionPrecision = 100;
		}
	}

	private void calculateErrors() {
		logger.info("Calculating evaluation errors for simulation " + simulation.getId());
		List<UserAppliance> predictedAppliancesToAnalyze = new ArrayList<UserAppliance>();
		
		// add both the predicted and actual appliances to one list=
		predictedAppliancesToAnalyze.addAll(predictedEnergyUsage.keySet());
		
		
		// go through simulated appliances first, looking for pairs
		for(SimulatedAppliance simulatedAppliance : simulation.getSimulatedAppliances()) {
			
			// get the matching predicted user appliance energy timesteps from the simulated appliance
			UserAppliance labeledAppliance = simulatedAppliance.getLabeledAppliance();
			List<EnergyTimestep> predictedApplianceTimesteps = new ArrayList<EnergyTimestep>();
			
			if(labeledAppliance != null) {
				predictedApplianceTimesteps = predictedEnergyUsage.get(labeledAppliance);
				
				// remove the matched predicted appliance so that we don't double count it in the next match-up
				predictedAppliancesToAnalyze.remove(labeledAppliance);
			}
				
			long applianceEnergyErrorInWattHours = calculateApplianceEnergyError(simulatedAppliance.getEnergyTimesteps(), predictedApplianceTimesteps);
			applianceEnergyErrors.put(labeledAppliance, applianceEnergyErrorInWattHours);
			overallEnergyError += applianceEnergyErrorInWattHours;
			
			logger.info(simulatedAppliance + " has error of " + (applianceEnergyErrorInWattHours) + " Watt-Hours");
		}
		

		calculateEnergyPredictionAccuracy();
		
		// TODO - now look at remaining appliances which were detected which were not labeled
		logger.warn("Skipping error calcuation for predicted appliances which did not have matching labeled simulation appliances");
		/*for(UserAppliance appliance : predictedAppliancesToAnalyze) {
			// if we have not already looked at this appliance
			if(!finishedAppliances.contains(appliance)) {
				
				Appliance matchingAppliance = findMatchingAppliance()
				
				finishedAppliances.add(appliance);
				if(matchingAppliance != null) {
					finishedAppliances.add(matchingAppliance);
				}
			}
			
			float applianceError = calculateApplianceEnergyError(appliance);
			overallEnergyError += applianceError;
			applianceEnergyErrors.put(appliance, applianceError);
		}*/
	}
	
	private void calculateEnergyPredictionAccuracy() {

		double denom = Math.max(1,calculateEnergyPredictionAccuracyDenominator());
		double numerator = calculateEnergyPredictionAccuracyNumerator();
		
		overallAccuracy = Math.max((100.f) * (1.0f - (numerator / (2.0f * denom))), 0); 
	}

	private double calculateEnergyPredictionAccuracyDenominator() {
		long value = 0;
		
		for(SimulatedAppliance simulatedAppliance : simulation.getSimulatedAppliances()) {
			for(EnergyTimestep timestep : simulatedAppliance.getEnergyTimesteps()) {
				value += timestep.getEnergyConsumed();
			}
		}
		
		return value;
	}

	private double calculateEnergyPredictionAccuracyNumerator() {
		long value = 0;
		
		for(SimulatedAppliance simulatedAppliance : simulation.getSimulatedAppliances()) {
			if(simulatedAppliance.getLabeledAppliance() != null) {
				value += applianceEnergyErrors.get(simulatedAppliance.getLabeledAppliance()) * 3600;
			}
		}
		
		return value;
	}

	private long calculateApplianceEnergyError(List<EnergyTimestep> actual, List<EnergyTimestep> predicted) {
		if(actual == null) {
			actual = new ArrayList<EnergyTimestep>();
		}
		if(predicted == null) {
			predicted = new ArrayList<EnergyTimestep>();
		}
		
		double diff = 0;
		int i = 0, j = 0;
		
		while(i < actual.size() || j < predicted.size()) {
			EnergyTimestep actualEnergyTimestep = i < actual.size() ? actual.get(i) : null;
			EnergyTimestep predictedEnergyTimestep = j < predicted.size() ? predicted.get(j) : null;
			
			
			if(predictedEnergyTimestep != null && actualEnergyTimestep != null) {
				// predicted is present
				if(actualEnergyTimestep.getStartTime().equals(predictedEnergyTimestep.getStartTime())) {
					diff += Math.abs(actualEnergyTimestep.getEnergyConsumed() - predictedEnergyTimestep.getEnergyConsumed()) / (double)3600;
					
					// advance both actual and predicted
					i++;
					j++;
				} else if(actualEnergyTimestep.getStartTime().getTime() < predictedEnergyTimestep.getStartTime().getTime()) {
					// predicted timestamp is later than current actual - error is actual measurement
					diff += ((double)actualEnergyTimestep.getEnergyConsumed()) / (double)3600;
					// advance actual
					i++;
				} else {
					// predicted timestamp is earlier then current actual - advance predicted
					diff += ((double)predictedEnergyTimestep.getEnergyConsumed()) / (double)3600;
					j++;
				}
			} else if(predictedEnergyTimestep != null) {
				diff += ((double)predictedEnergyTimestep.getEnergyConsumed()) / (double)3600;
				j++;
			} else {
				// predicted is missing - error is actual measurement
				diff += ((double)actualEnergyTimestep.getEnergyConsumed()) / (double)3600;
				i++;
			}
		}
		
		return (long)diff;
	}

	public Simulation getSimulation() {
		return simulation;
	}
	
	public Map<UserAppliance, List<EnergyTimestep>> getActualEnergyUsage() {
		Map<UserAppliance, List<EnergyTimestep>> actualUsage = new HashMap<UserAppliance, List<EnergyTimestep>>();
		
		for(SimulatedAppliance simulatedAppliance : simulation.getSimulatedAppliances()) {
			actualUsage.put(simulatedAppliance, simulatedAppliance.getEnergyTimesteps());
		}
		
		return actualUsage;
	}

	public Map<UserAppliance, List<EnergyTimestep>> getPredictedEnergyUsage() {
		return predictedEnergyUsage;
	}
	
	public long getOverallEnergyError() {
		return overallEnergyError;
	}
	
	public double getOverallAccuracy() {
		return overallAccuracy;
	}

	public double getStateTransitionAccuracy() {
		return stateTransitionAccuracy;
	}

	public double getStateTransitionRecall() {
		return stateTransitionRecall;
	}

	public double getStateTransitionPrecision() {
		return stateTransitionPrecision;
	}

	public Map<UserAppliance, Long> getApplianceEnergyErrors() {
		return applianceEnergyErrors;
	}

	public Map<UserAppliance, List<ApplianceStateTransition>> getPredictedApplianceStateTransitions() {
		return predictedApplianceStateTransitions;
	}

	public String toString() {
		return simulation.toString();
	}

	public void dumpInternalData() {
		predictedEnergyUsage = null;
		applianceEnergyErrors = null;
		predictedApplianceStateTransitions = null;
	}
}
