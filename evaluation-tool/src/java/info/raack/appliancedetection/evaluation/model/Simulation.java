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

import info.raack.appliancedetection.common.util.DateUtils;
import info.raack.appliancedetection.evaluation.model.appliance.SimulatedAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

public class Simulation {
	
	private Logger logger = LoggerFactory.getLogger(Simulation.class);
	
	private String id;
	private long durationInSeconds;
	private int numAppliances;
	private int labelsPerOnOff;
	private int onConcurrency;
	
	protected Date startTime;

	private boolean dataQueried = false;
	
	private List<SimulatedAppliance> appliances;
	private List<SimulatedAppliance> noLabelAppliances;
	
	private List<SecondData> secondData;
	
	private int maxUnlabeledAppliances;
	
	private DateUtils dateUtils;
	
	private SimulationGroup group;
	
	// used when creating simulation for first time
	public Simulation(DateUtils dateUtils, Date startTime, long durationInSeconds, int numAppliances, int labelsPerOnOff, int onConcurrency, List<SimulatedAppliance> possibleAppliances, SimulationGroup group) {
		this.dateUtils = dateUtils;
		this.durationInSeconds = durationInSeconds;
		this.numAppliances = numAppliances;
		this.labelsPerOnOff = labelsPerOnOff;
		this.onConcurrency = onConcurrency;
		this.id = UUID.randomUUID().toString();
		this.group = group;
		
		// random number of unlabeled appliances (between 1 and 5)
		this.maxUnlabeledAppliances = (int)Math.ceil((Math.random() * 5));
		
		setStartTime(startTime.getTime());
		
		logger.debug("Starting simulation " + id + " at " + startTime);
		
		constructAppliances(possibleAppliances);
	}

	private void setStartTime(long timeInMillis) {
		Calendar cal = new GregorianCalendar();
		cal.setTimeInMillis(timeInMillis);
		
		// round start time to 5 minute marker to greatly simplify evaluation
		cal = dateUtils.getPreviousFiveMinuteIncrement(cal);
		
		startTime = cal.getTime();
		
		logger.debug("Setting start time for simulated data to be " + startTime);
	}
	
	public void incrementTimeForTestDataSet() {
		Calendar cal = new GregorianCalendar();
		cal.setTime(startTime);
		cal.add(Calendar.SECOND, (int)durationInSeconds);
		setStartTime(cal.getTimeInMillis());
	}
	
	// used when re-constructing simulation from database access code
	public Simulation(String simulationId, Date startTime, long duration, int numAppliances, int onConcurrency, int labelsPerAppliance, List<SimulatedAppliance> appliances, List<SecondData> secondData) {
		this.durationInSeconds = duration;
		this.numAppliances = numAppliances;
		this.labelsPerOnOff = labelsPerAppliance;
		this.onConcurrency = onConcurrency;
		this.id = simulationId;
		this.startTime = startTime;
		this.appliances = appliances;
		this.secondData = secondData;
	}
	
	/**
	 * Resets all of the simulated data so far so that the simulation may be run again with the same parameters for time, appliances, labels, and concurrency.
	 */
	public void reset() {
		secondData = null;
		dataQueried = false;
		for(SimulatedAppliance appliance : appliances) {
			appliance.reset();
		}
	}

	public Date getStartTime() {
		return startTime;
	}
	
	public Date getEndTime() {
		Calendar cal = new GregorianCalendar();
		cal.setTime(startTime);
		cal.add(Calendar.SECOND, (int)durationInSeconds);
		return cal.getTime();
	}
	
	public String getId() {
		return id;
	}
	
	public long getDurationInSeconds() {
		return durationInSeconds;
	}

	public int getNumAppliances() {
		return numAppliances;
	}

	public int getLabelsPerOnOff() {
		return labelsPerOnOff;
	}

	public int getOnConcurrency() {
		return onConcurrency;
	}

	public String toString() {
		return startTime + "; " + DurationFormatUtils.formatDurationWords(durationInSeconds * 1000L, true, true) + "; " + numAppliances + " appliances; " + labelsPerOnOff + " labels per appliance; " + onConcurrency + " max appliances on at once";
	}

	/**
	 * Effectively runs the simulation, returning all of the simulated power draw data
	 * 
	 * @return a list of SecondData entries specifying the power draw during the simulation period
	 */
	public List<SecondData> getSecondData() {
		// ensure that the same data does not get returned twice
		synchronized(this) {
			if(dataQueried) {
				return Collections.emptyList();
			} else {
				dataQueried = true;
				return secondData;
			}
		}
	}
	
	public void run() {
		synchronized(this) {
			if(secondData == null) {
				logger.info("Generating simulated data for simulation " + this);
				// start time for this simulation is now
				Calendar cal = new GregorianCalendar();
				cal.setTime(startTime);
				
				// create list of second data
				secondData = new ArrayList<SecondData>();
				
				// increment timestep and calendar by one second each loop
				for(int timestep = 0 ; timestep < durationInSeconds; timestep++, cal.add(Calendar.SECOND, 1)) {
					int power = 0;
					
					//logger.debug("Simulation " + this.toString() + " at timestep " + timestep);
					
					// compute total power as the summation of all power drawn from each simulated appliance at this timestep
					for(SimulatedAppliance appliance : getSimulatedAppliances()) {
						power += appliance.getPowerAtTimestep(cal, timestep);
					}
					for(SimulatedAppliance appliance : noLabelAppliances) {
						power += appliance.getPowerAtTimestep(cal, timestep);
					}
					
					SecondData data = new SecondData();
					data.setCalLong(cal.getTimeInMillis());
					
					data.setPower(power);
					data.setVoltage(120.0f);
					
					secondData.add(data);
				}
			}
		}
	}
	
	public Map<SimulatedAppliance, List<ApplianceStateTransition>> getApplianceOnOffs() {
		Map<SimulatedAppliance, List<ApplianceStateTransition>> transitions = new HashMap<SimulatedAppliance, List<ApplianceStateTransition>>();
		
		for(SimulatedAppliance appliance : getSimulatedAppliances()) {
			transitions.put(appliance, appliance.getApplianceOnOffs());
		}
		
		return transitions;
	}

	public List<SimulatedAppliance> getSimulatedAppliances() {
		return appliances;
	}

	private void constructAppliances(List<SimulatedAppliance> possibleAppliances) {
		appliances = new ArrayList<SimulatedAppliance>();
		
		// simulate all of the appliances
		for(int i = 0; i < numAppliances; i++) {
			int applianceNum = (int)((float)possibleAppliances.size() * Math.random());
			
			// create the requested appliance and initialize it
			SimulatedAppliance simulatedAppliance = possibleAppliances.get(applianceNum).copy().initialize(this, i+1, true);
			
			logger.info("Simulation " + toString() + " creating " + simulatedAppliance.getName());
			
			// now add the simulated appliance to the list
			appliances.add(simulatedAppliance);
		}
		
		noLabelAppliances = new ArrayList<SimulatedAppliance>();
		// now add three appliances which do not generate labels
		for(int i = numAppliances; i < numAppliances + maxUnlabeledAppliances; i++) {
			int applianceNum = (int)((float)possibleAppliances.size() * Math.random());
			
			// create the requested appliance and initialize it
			SimulatedAppliance simulatedAppliance = possibleAppliances.get(applianceNum).copy().initialize(this, i+1, false);
			
			logger.info("Simulation " + toString() + " creating " + simulatedAppliance.getName() + " as no label appliance");
			
			// now add the simulated appliance to the list
			noLabelAppliances.add(simulatedAppliance);
		}
	}

	public boolean canTurnOn() {
		return countAppliancesOn() < onConcurrency && !isTrainingHappening();
	}
	
	private int countAppliancesOn() {
		int count = 0;
		
		for(SimulatedAppliance appliance : appliances) {
			count += appliance.isOn() ? 1 : 0;
		}
		
		return count;
	}

	public SimulationGroup getGroup() {
		return group;
	}

	public void setGroup(SimulationGroup group) {
		this.group = group;
	}

	public boolean isTrainingHappening() {
		for(SimulatedAppliance appliance : appliances) {
			if(appliance.isTraining()) {
				return true;
			}
		}
		return false;
	}

	public boolean canTrain() {
		return countAppliancesOn() == 0;
	}
}
