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
import info.raack.appliancelabeler.model.AlgorithmPredictions;
import info.raack.appliancelabeler.model.EnergyTimestep;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.energymonitor.EnergyMonitor;
import info.raack.appliancelabeler.service.DataService.LabelResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

/**
 * The naive algorithm looks at spans of x seconds and determines if there was a x watt jump in power consumption. If so, it is a state transition is created.
 * @author traack
 *
 */
@Component
public class NaiveStateTransitionDetectionAlgorithm extends ApplianceEnergyConsumptionDetectionAlgorithm {
	private Logger logger = LoggerFactory.getLogger(NaiveStateTransitionDetectionAlgorithm.class);
	
	@Autowired
	private Database database;

	public String getAlgorithmName() {
		return "naive";
	}

	public int getId() {
		return 1;
	}
	
	// random
	public AlgorithmPredictions algorithmCalculateApplianceEnergyUsePredictions(EnergyMonitor energyMonitor, Queue<EnergyTimestep> originTimesteps, ItemReader<SecondData> dataReader) {
		AlgorithmPredictions algorithmPredictions = new AlgorithmPredictions();
		
		Map<UserAppliance, List<EnergyTimestep>> applianceTimesteps = new HashMap<UserAppliance, List<EnergyTimestep>>();
		
		List<UserAppliance> apps = database.getUserAppliancesForAlgorithmForEnergyMonitor(energyMonitor, getId());
		
		Map<UserAppliance, Double> currentTimestepEnergyConsumption = new HashMap<UserAppliance, Double>();
		
		for(UserAppliance appliance : apps) {
			currentTimestepEnergyConsumption.put(appliance, 0d);
			applianceTimesteps.put(appliance, new ArrayList<EnergyTimestep>());
		}
		
		EnergyTimestep currentTimestep = originTimesteps.poll();

		// ASSUMPTION - measurements are in chronological order
		if(apps.size() > 0){

			long currentTimestepEndTime = currentTimestep.getEndTime().getTime();
			
				// for each second in the measurement list
				int number = 0;
				
				try {
					for(SecondData secondData = dataReader.read(); secondData != null; secondData = dataReader.read()) {
						long dateLong = secondData.getCalLong();
						
						while(dateLong > currentTimestepEndTime) {
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
						
						//logger.debug("Current second: " + new Date(dateLong) + " (" + dateLong + ")");
						// power during this second, watts, multiplied by the amount of time, 1 second, is one 1 watt second (one joule)
						double consumedEnergy = secondData.getPower();
						
						int relativeAmounts[] = new int[apps.size()]; 
						
						int total = 0;
						for(int i = 0; i < apps.size(); i++) {
							// randomly assign energy usage to each appliance - don't even attempt to do any actual prediction
							relativeAmounts[i] = (int)(Math.random() * 100f);
							total += relativeAmounts[i];
						}
						
						for(int i = 0; i < apps.size(); i++) {
							// compute percentage
							double previousConsumption = currentTimestepEnergyConsumption.get(apps.get(i));
							double newConsumption = consumedEnergy * ((double)relativeAmounts[i] / (double)total);
							
							currentTimestepEnergyConsumption.put(apps.get(i), previousConsumption + newConsumption);
						}
						
						//logger.debug("Current millis: " + date.getTime() + "; ending millis: " + currentTimestep.getEndTime().getTime());
						
						if(dateLong == currentTimestepEndTime) {
							//logger.debug("End of timestep; closing energy measurement");
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
					throw new RuntimeException("Could not train naive algorithm", e);
				}
			}
		
		
		algorithmPredictions.setEnergyTimesteps(applianceTimesteps);
		algorithmPredictions.setStateTransitions(new ArrayList<ApplianceStateTransition>());
		
		return algorithmPredictions;
	}

	public AlgorithmResult train(EnergyMonitor monitor, ItemReader<SecondData> data) {
		// no training
		return new AlgorithmResult(monitor, this, new Integer(0));
	}

	@Override
	public LabelResult detectAcceptableUserTraining(ItemReader<SecondData> dataReader) {
		// does not matter what I return - training doesn't help
		return LabelResult.OK;
	}
}
