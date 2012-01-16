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
package info.raack.appliancelabeler.data;

import info.raack.appliancelabeler.data.batch.ItemReader;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.AlgorithmResult;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.ApplianceEnergyConsumptionDetectionAlgorithm;
import info.raack.appliancelabeler.model.AlgorithmPredictions;
import info.raack.appliancelabeler.model.Appliance;
import info.raack.appliancelabeler.model.EnergyTimestep;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.GenericStateTransition;
import info.raack.appliancelabeler.model.energymonitor.EnergyMonitor;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.security.oauth.consumer.token.OAuthConsumerToken;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

public interface Database {
	// Methods for operating on raw energy monitor data
	public void storeData(EnergyMonitor energyMonitor, List<SecondData> data, Long lastOffset);

	public Long getLastDatapoint(EnergyMonitor energyMonitor);
	public Date getLastMeasurementTimeForEnergyMonitor(EnergyMonitor energyMonitor);
	
	public float getEnergyCost(EnergyMonitor energyMonitor);
	public void setEnergyCost(EnergyMonitor energyMonitor, float costPerKwh);
	
	public List<Appliance> getAllAppliances();
	public Appliance getApplianceById(int applianceId);
	public List<String> getAllUserIds();
	public UserAppliance getUserApplianceById(int id);
	public List<UserAppliance> getUserAppliancesFromUserForEnergyMonitor(EnergyMonitor energyMonitor);
	public List<UserAppliance> getUserAppliancesForAlgorithmForEnergyMonitor(EnergyMonitor energyMonitor, int algorithmId);
	public void addUserAppliance(EnergyMonitor energyMonitor, UserAppliance userAppliance);
	
	public List<SecondData> getEnergyMeasurementsForMonitor(EnergyMonitor energyMonitor, Date start, Date end, int ticks);
	public ItemReader<SecondData> getEnergyMeasurementReaderForMonitor(EnergyMonitor energyMonitor, Date start, Date end, int ticks);
	public List<SecondData> getEnergyMeasurementsForMonitor(EnergyMonitor energyMonitor, long startingMeasurement, long numberOfMeasurements);

	public List<Integer> getAlgorithmIdsForCurrentEnergyConsumptionForMonitor(EnergyMonitor energyMonitor);
	public List<Integer> getAlgorithmIdsForCurrentStateTranstionsForMonitor(EnergyMonitor energyMonitor);
	public List<EnergyMonitor> getEnergyMonitors();
	
	// Methods for operating on detected state transitions
	public List<ApplianceStateTransition> getUserOnOffLabels(EnergyMonitor energyMonitor);
	public List<ApplianceStateTransition> getPredictedApplianceStateTransitionsForMonitor(EnergyMonitor energyMonitor, int algorithmId, Date start, Date end, boolean anonymousAppliances);
	public List<ApplianceStateTransition> getAnonymousApplianceStateTransitionsForMonitor(EnergyMonitor energyMonitor, int algorithmId, Date start, Date end);
	public void storeAlgorithmPredictions(EnergyMonitor energyMonitor, Map<Integer, AlgorithmPredictions> algorithmPredictions);
	public void storeUserOnOffLabels(List<ApplianceStateTransition> labels);
	public void removeStateTransitionAndEnergyPredictionsForAlgorithmAndMonitor(ApplianceEnergyConsumptionDetectionAlgorithm algorithm, EnergyMonitor energyMonitor);

	// Methods for operating on disaggregated appliance energy consumption predictions
	public List<EnergyTimestep> getApplianceEnergyConsumptionForMonitor(EnergyMonitor energyMonitor, int algorithmId, Date start, Date end);
	
	// Methods for operating on user data
	public void saveOAuthTokensForUserId(String userId, String email, Map<String, OAuthConsumerToken> tokens);
	public Map<String, OAuthConsumerToken> getOAuthTokensForUserId(String userId);
	public Map<String, Map<String, OAuthConsumerToken>> getOAuthTokensForAllUsers();

	public AlgorithmResult getAlgorithmResultForMonitorAndAlgorithm(EnergyMonitor monitor, ApplianceEnergyConsumptionDetectionAlgorithm algorithm);
	public void saveAlgorithmResult(AlgorithmResult result);

	public Map<UserAppliance, ApplianceStateTransition> getLatestApplianceStatesForUserAppliances(List<UserAppliance> apps, final ApplianceEnergyConsumptionDetectionAlgorithm algorithm);

	public String getUserEmailForUserId(String userId);
	
	public EnergyMonitor getEnergyMonitor(String userId, String monitorId, String monitorType);
	public Map<Appliance, Double> getApplianceEnergyConsumptionAverages(Date startDate, int detectionAlgorithmId, EnergyMonitor ignoredMonitor);

	public GenericStateTransition getAnonymousApplianceStateTransitionById(int transitionId);

	public void removeTransition(GenericStateTransition transition);

}
