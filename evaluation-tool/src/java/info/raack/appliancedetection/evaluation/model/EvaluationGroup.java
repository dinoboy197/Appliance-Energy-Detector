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

import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.ApplianceEnergyConsumptionDetectionAlgorithm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;

public class EvaluationGroup {
	
	private SimulationGroup simulationGroup;
	
	// mean, median, standard deviation
	private Map<ApplianceEnergyConsumptionDetectionAlgorithm, Double[]> errorMetrics = new HashMap<ApplianceEnergyConsumptionDetectionAlgorithm, Double[]>();
	private Map<ApplianceEnergyConsumptionDetectionAlgorithm, Double[]> accuracyErrorMetrics = new HashMap<ApplianceEnergyConsumptionDetectionAlgorithm, Double[]>();
	private Map<ApplianceEnergyConsumptionDetectionAlgorithm, Double[]> stateTransitionAccuracyErrorMetrics = new HashMap<ApplianceEnergyConsumptionDetectionAlgorithm, Double[]>();
	private Map<ApplianceEnergyConsumptionDetectionAlgorithm, Double[]> stateTransitionRecallErrorMetrics = new HashMap<ApplianceEnergyConsumptionDetectionAlgorithm, Double[]>();
	private Map<ApplianceEnergyConsumptionDetectionAlgorithm, Double[]> stateTransitionPrecisionErrorMetrics = new HashMap<ApplianceEnergyConsumptionDetectionAlgorithm, Double[]>();
	
	// not used in this class directly, but it is used in javascript and is serialized directly by gson
	private Map<ApplianceEnergyConsumptionDetectionAlgorithm, List<Evaluation>> algorithmEvaluations = new HashMap<ApplianceEnergyConsumptionDetectionAlgorithm, List<Evaluation>>();

	public EvaluationGroup(SimulationGroup simulationGroup, Map<ApplianceEnergyConsumptionDetectionAlgorithm, List<Evaluation>> algorithmEvaluations) {
		this.simulationGroup = simulationGroup;
		this.algorithmEvaluations = algorithmEvaluations;
		
		calculateEvaluationMetrics(algorithmEvaluations);
	}

	private void calculateEvaluationMetrics(Map<ApplianceEnergyConsumptionDetectionAlgorithm, List<Evaluation>> evaluationInfo) {
		for(ApplianceEnergyConsumptionDetectionAlgorithm algorithm : evaluationInfo.keySet()) {
			// do wattage stats
			DescriptiveStatistics stats = new DescriptiveStatistics();

			List<Evaluation> evaluationList = evaluationInfo.get(algorithm);
			if(evaluationList.size() == 0) {
				throw new IllegalArgumentException("No evaluations for " + algorithm + " in simulation group " + simulationGroup);
			}
 			for(Evaluation evaluation : evaluationList) {
 				// calculation produces watts
				stats.addValue((double)(evaluation.getOverallEnergyError()) * (3600.0 / (double)(evaluation.getSimulation().getDurationInSeconds())));
			}

 			
			errorMetrics.put(algorithm, new Double[] {stats.getMean(), stats.getPercentile(50), stats.getStandardDeviation()});
			
			//
			stats = new DescriptiveStatistics();

			evaluationList = evaluationInfo.get(algorithm);
			if(evaluationList.size() == 0) {
				throw new IllegalArgumentException("No evaluations for " + algorithm + " in simulation group " + simulationGroup);
			}
 			for(Evaluation evaluation : evaluationList) {
 				// calculation produces watts
				stats.addValue((double)(evaluation.getOverallAccuracy()));
			}

 			
 			accuracyErrorMetrics.put(algorithm, new Double[] {stats.getMean(), stats.getPercentile(50), stats.getStandardDeviation()});
 			
 			//
			stats = new DescriptiveStatistics();

			evaluationList = evaluationInfo.get(algorithm);
			if(evaluationList.size() == 0) {
				throw new IllegalArgumentException("No evaluations for " + algorithm + " in simulation group " + simulationGroup);
			}
 			for(Evaluation evaluation : evaluationList) {
 				// calculation produces watts
				stats.addValue((double)(evaluation.getStateTransitionAccuracy()));
			}

 			
 			stateTransitionAccuracyErrorMetrics.put(algorithm, new Double[] {stats.getMean(), stats.getPercentile(50), stats.getStandardDeviation()});
 			
 			//
			stats = new DescriptiveStatistics();

			evaluationList = evaluationInfo.get(algorithm);
			if(evaluationList.size() == 0) {
				throw new IllegalArgumentException("No evaluations for " + algorithm + " in simulation group " + simulationGroup);
			}
 			for(Evaluation evaluation : evaluationList) {
 				// calculation produces watts
				stats.addValue((double)(evaluation.getStateTransitionRecall()));
			}

 			
 			stateTransitionRecallErrorMetrics.put(algorithm, new Double[] {stats.getMean(), stats.getPercentile(50), stats.getStandardDeviation()});
 			
 			//
			stats = new DescriptiveStatistics();

			evaluationList = evaluationInfo.get(algorithm);
			if(evaluationList.size() == 0) {
				throw new IllegalArgumentException("No evaluations for " + algorithm + " in simulation group " + simulationGroup);
			}
 			for(Evaluation evaluation : evaluationList) {
 				// calculation produces watts
				stats.addValue((double)(evaluation.getStateTransitionPrecision()));
			}

 			
 			stateTransitionPrecisionErrorMetrics.put(algorithm, new Double[] {stats.getMean(), stats.getPercentile(50), stats.getStandardDeviation()});
		}
	}
	
}
