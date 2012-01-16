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
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.powerspike.PowerSpike;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.powerspike.SlidingWindowPowerSpikeDetector;
import info.raack.appliancelabeler.model.Appliance;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.PowerDeltaStateTransition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

/**
 * Uses only high confidence predictions through Weka probability limits and and checking of power spike similarity to known spikes.
 * @author traack
 *
 */
@Component
public class HighConfidenceFSMPowerSpikeDetectionAlgorithm extends PowerDrawSlidingWindowMultiPartFlatLoadDetectionAlgorithm {
	private Logger logger = LoggerFactory.getLogger(HighConfidenceFSMPowerSpikeDetectionAlgorithm.class);
	
	private int spikeThreshold = 75;
	
	@Override
	protected double getDetectionProbabilityThreshold() {
		return 0.7;
	}
	
	public Map<Long, List<ApplianceStateTransition>> detectStateTransitionsInternal(AlgorithmResult algorithmResult, UserAppliance fallbackAppliance, ItemReader<SecondData> dataReader, int modelId) {

		Map<UserAppliance,List<ApplianceStateTransition>> intermediateTransitions = new HashMap<UserAppliance, List<ApplianceStateTransition>>();
		
		// ASSUMPTION - data is already sorted in chronological order
		
		try {
			SlidingWindowPowerSpikeDetector spikeDetector = new SlidingWindowPowerSpikeDetector(stableTime, stabilityThreshold, differenceThreshold);
		
			List<double[]> trainingInstancesWithClassLabels = null;
			Map<Integer, Integer[]> trainingInstanceLimits = null;
			
			if(algorithmResult != null) {
				trainingInstancesWithClassLabels = mlEngine.getTrainingInstancesWithClassLabels(modelId);
				trainingInstanceLimits = computeTrainingInstanceSpikeLimits(trainingInstancesWithClassLabels);
			}
			
			Appliance otherAppliance = new Appliance();
			otherAppliance.setId(15);
			UserAppliance autogeneratedAppliance = new UserAppliance(-1, otherAppliance, "autogenerated appliance", getId(), true);
			
			for(SecondData currentPoint = dataReader.read(); currentPoint != null; currentPoint = dataReader.read()) {
				
				PowerSpike spike = spikeDetector.detectCurrentTransition(currentPoint);
				
				if(spike != null) {
					// mark this as a transition
					// use model to predict the appliance id
					boolean training = fallbackAppliance == null;
					
					UserAppliance userAppliance = null;
					
					if(algorithmResult != null) {
						boolean foundAppliance = false;
						if(isSpikeNearbyAnyTrainedSpikes(spike, trainingInstancesWithClassLabels, trainingInstanceLimits)) {
							
							int userApplianceId = mlEngine.predictWithModel(modelId, new double[] {Math.abs(spike.getSpike()), getMinuteOfDay(spike.getDate()), spike.getTransitionDuration()}, getDetectionProbabilityThreshold());
							if(userApplianceId != -1) {
								foundAppliance = true;
								
								// get the user appliance that corresponds to this user appliance id
								userAppliance = database.getUserApplianceById(userApplianceId);
								logger.debug("user appliance " + userApplianceId + " predicted for " + Math.abs(spike.getSpike()) + "; " + userAppliance + " ");
							}
						} 
						if(!foundAppliance) {
							// spike has not been associated with a particular appliance
							userAppliance = autogeneratedAppliance;
						}
					}
					
					if(training || userAppliance != null) {
						// don't create a state transition if we had a model but didn't match an appliance with the current spike
						final PowerDeltaStateTransition newTransition = new PowerDeltaStateTransition(-1, userAppliance, this.getId(), spike.getSpike() > 0, spike.getDate(), spike.getSpike(), getMinuteOfDay(spike.getDate()), spike.getTransitionDuration());
	
						if(intermediateTransitions.containsKey(userAppliance)) {
							intermediateTransitions.get(userAppliance).add(newTransition);
						} else {
							intermediateTransitions.put(userAppliance, new ArrayList<ApplianceStateTransition>() {{ add(newTransition); }});
						}
					}
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Cannot detect state transitions", e);
		}
		
		return processOnOffPairs(intermediateTransitions);
	}
	
	
	private Map<Integer, Integer[]> computeTrainingInstanceSpikeLimits(List<double[]> trainingInstancesWithClassLabels) {
		Map<Integer, List<Double>> trainingSpikes = new HashMap<Integer, List<Double>>();
		
		// collect all spikes for each class
		for(double[] instance : trainingInstancesWithClassLabels) {
			double clazz = instance[instance.length - 1];
			if(clazz != missingValue) {
				double trainingSpike = instance[0];
				
				if(!trainingSpikes.containsKey((int)clazz)) {
					trainingSpikes.put((int)clazz, new ArrayList<Double>());
				}
				trainingSpikes.get((int)clazz).add(trainingSpike);
			}
		}
		
		Map<Integer, Integer[]> trainingInstanceLimits = new HashMap<Integer, Integer[]>();
		
		// calculate interval one standard deviation away from mean of labeled power spikes for each class
		for(Integer clazz : trainingSpikes.keySet()) {
			DescriptiveStatistics stats = new DescriptiveStatistics();
			for(Double spikeValue : trainingSpikes.get(clazz)) {
				stats.addValue(spikeValue);
			}
			trainingInstanceLimits.put(clazz,new Integer[] {(int)(stats.getMean() - stats.getStandardDeviation()), (int)(stats.getMean() + stats.getStandardDeviation()) });
		}
		
		return trainingInstanceLimits;
	}

	private boolean isSpikeNearbyAnyTrainedSpikes(PowerSpike spike, List<double[]> trainingInstances, Map<Integer, Integer[]> trainingInstanceLimits) {
		

		// tested spike must be within the spikeThreshold of any trained spike and within one standard deviation of the mean spike amplitude for the class of that trained spike
		// this accounts for being near a large or small spike cluster (roughly)
		
		for(double[] instance : trainingInstances) {
			double clazz = instance[instance.length - 1];
			if(clazz != missingValue) {
				double trainingSpike = instance[0];
				
				if(Math.abs(spike.getSpike() - trainingSpike) <= spikeThreshold) {
					// check spike limits
					Integer[] limits = trainingInstanceLimits.get((int)clazz);
					if(limits != null) {
						if(limits[0] <= spike.getSpike() && spike.getSpike() <= limits[1]) {
							return true;
						}
					}
				}
			}
		}
		
		
		return false;
	}

	public String getAlgorithmName() {
		return "high-confidence-pd-sw-fl";
	}

	public int getId() {
		return 6;
	}
}
