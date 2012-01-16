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
package info.raack.appliancelabeler.web.json;

import info.raack.appliancelabeler.model.EnergyTimestep;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

public class EnergyWrapper {

	private List<long[]> secondData;
	// watt-hours
	private Map<UserAppliance, List<EnergyTimestep>> predictedEnergyUsage;
	
	// watt-hours
	private Map<UserAppliance, Double> predictedEnergyUsageTotals;
	
	// dollars
	private Map<UserAppliance, Double> predictedEnergyCostTotals;
	
	private Map<UserAppliance,Integer> predictedEnergyUsageIdMap;
	private Map<UserAppliance,String> userApplianceNameMap;
	private Map<UserAppliance, List<ApplianceStateTransition>> predictedApplianceStateTransitions;
	
	public EnergyWrapper(List<SecondData> data, Map<UserAppliance, List<EnergyTimestep>> predictedEnergyUsage, Map<UserAppliance, List<ApplianceStateTransition>> predictedApplianceStateTransitions, float costPerKwh) {
		loadPredictedEnergyUsage(predictedEnergyUsage, costPerKwh);
		loadSecondData(data);		
		createPredictedEnergyUsageMap();
		this.predictedApplianceStateTransitions = predictedApplianceStateTransitions;
	}
	
	private void loadPredictedEnergyUsage(Map<UserAppliance, List<EnergyTimestep>> predictedEnergyUsage, float costPerKwh) {
		this.predictedEnergyUsage = predictedEnergyUsage;
		
		for(List<EnergyTimestep> timesteps : predictedEnergyUsage.values()) {
			for(EnergyTimestep timestep : timesteps) {
				timestep.setEnergyConsumed(timestep.getEnergyConsumed() / (double)3600);
			}
		}
		
		predictedEnergyUsageTotals = new HashMap<UserAppliance,Double>();
		
		for(UserAppliance app : predictedEnergyUsage.keySet()) {
			double total = 0;
			for(EnergyTimestep timestep : predictedEnergyUsage.get(app)) {
				total += timestep.getEnergyConsumed();
			}
			predictedEnergyUsageTotals.put(app, total);
			if(costPerKwh > 0) {
				if(predictedEnergyCostTotals == null) {
					predictedEnergyCostTotals = new HashMap<UserAppliance,Double>();
				}
				predictedEnergyCostTotals.put(app, (double)total * costPerKwh / (double)1000);
			}
		}
	}

	private void loadSecondData(List<SecondData> data) {
		secondData = new ArrayList<long[]>();
		for(SecondData point : data) {
			secondData.add(new long[] {point.getCalLong(), point.getPower()});
		}
	}
	
	private void createPredictedEnergyUsageMap() {
		predictedEnergyUsageIdMap = new HashMap<UserAppliance,Integer>();
		userApplianceNameMap = new HashMap<UserAppliance,String>();
		
		for(UserAppliance appliance : predictedEnergyUsage.keySet()) {
			predictedEnergyUsageIdMap.put(appliance, appliance.getId());
			userApplianceNameMap.put(appliance, appliance.getName());
		}
	}
}
