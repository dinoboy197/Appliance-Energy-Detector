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
package info.raack.appliancedetection.evaluation.model.appliance;

import info.raack.appliancedetection.evaluation.model.Simulation;
import info.raack.appliancelabeler.model.Appliance;
import info.raack.appliancelabeler.model.EnergyTimestep;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.GenericStateTransition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.NormalDistributionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SimulatedAppliance extends UserAppliance {

	private Logger logger = LoggerFactory.getLogger(SimulatedAppliance.class);
	
	private static int TIMESTEP_DURATION = 60 * 5; // 5 minutes
	
	private boolean on = false;
	private int cycleSeconds = 0;
	private int cycleSecondsElapsed = 0;
	
	private int applianceNum;
	
	private Simulation simulation;

	public abstract SimulatedAppliance copy();
	
	public abstract String getType();
	
	protected abstract void applianceSpecificInitialization();
	protected abstract int maxOnPeriod();
	protected abstract int maxOffPeriod();
	protected abstract int powerDrawAtOnCycleTimestep(int totalSeconds, int secondIndex, int globalTimestep);
	
	private List<EnergyTimestep> energyTimesteps = new ArrayList<EnergyTimestep>();
	private List<ApplianceStateTransition[]> allOnOffPairs = new ArrayList<ApplianceStateTransition[]>();
	private List<ApplianceStateTransition> allStateTransitions = new ArrayList<ApplianceStateTransition>();
	
	private ApplianceStateTransition[] currentOnOffPair = new ApplianceStateTransition[2];
	
	private UserAppliance labeledAppliance;
	
	private double energyConsumedDuringTimestep = 0;
	
	protected int cycleTransitionRampDuration;
	
	private int trainingSessionsRemaining;
	private boolean inTraining = false;
	
	private NormalDistributionImpl noiseDistribution = new NormalDistributionImpl(0, Math.random() * 2f);

	public SimulatedAppliance() {
		// will be set in initialize
		super(-1, null, "", -1, false);
	}

	
	public final boolean isOn() {
		return on;
	}
	
	public final int getApplianceNum() {
		return applianceNum;
	}
	
	public final SimulatedAppliance initialize(Simulation simulation, int applianceNum, boolean generateLabels) {
		this.simulation = simulation;
		this.applianceNum = applianceNum;
		
		// keep appliance off at start
		cycleSeconds = (int)(Math.random() * (float)maxOffPeriod());
		
		// randomize ramp up time
		cycleTransitionRampDuration = (int)(Math.random() * 10f);
		
		// set the training counter
		trainingSessionsRemaining = generateLabels ? simulation.getLabelsPerOnOff() : 0;
		
		applianceSpecificInitialization();
		
		// TODO - this should be for a specific appliance type, not just a random number
		name = "simulated " + getType() + " " + applianceNum;
		appliance = new Appliance();
		appliance.setId(1);
		appliance.setDescription(getType());
		
		return this;
	}
	
	/**
	 * Resets the state of the appliance back to it's initial state so that more simulated data can be generated.
	 */
	public void reset() {
		on = false;
		energyTimesteps = new ArrayList<EnergyTimestep>();
		allOnOffPairs = new ArrayList<ApplianceStateTransition[]>();
		currentOnOffPair = new ApplianceStateTransition[2];
		allStateTransitions = new ArrayList<ApplianceStateTransition>();

		energyConsumedDuringTimestep = 0;
		
		cycleSeconds = (int)(Math.random() * (float)maxOffPeriod());
	}
	
	protected void addStateTransition(int timestep, boolean on) {
		allStateTransitions.add(new GenericStateTransition(1, null, 0, on, getCurrentSimulatedTime(timestep)));
	}
	
	public final int getPowerAtTimestep(Calendar cal, int timestep) {
		int powerDraw = 0;
		
		// am I on?
		if(!on) {
			// should I turn on?
			if(cycleSeconds == cycleSecondsElapsed) {
				cycleSecondsElapsed = 0;
				int newCycleSeconds = 0;
				
				// check the level of appliance on concurrency
				if(simulation.canTurnOn()) {
					// train if we need to and if we can
					if(trainingSessionsRemaining > 0 && simulation.canTrain()) {
						trainingSessionsRemaining--;
						inTraining = true;
						
						currentOnOffPair = new ApplianceStateTransition[2];
						currentOnOffPair[0] = new GenericStateTransition(-1, null, 0, true, getCurrentSimulatedTime(timestep) - 5*1000);
					}
					
					// the simulator says that we can turn on 
					on = true;
					
					
					// determine how long the appliance will be on
					newCycleSeconds = (int)(Math.random() * (float)maxOnPeriod());
					logger.debug("at " + cal.getTime() + " " + getName() + " turning on for " + newCycleSeconds + " seconds");
					
					addStateTransition(timestep, on);
					
				} else {
					// create another off cycle
					newCycleSeconds = (int)(Math.random() * (float)maxOffPeriod());
					logger.debug("at " + cal.getTime() + " " + getName() + " cannot turn on yet so sleeping " + newCycleSeconds + " longer");
					
				}
				
				cycleSeconds = newCycleSeconds;
			} else {
				cycleSecondsElapsed++;
			}
		} 
		
		if(on) {
			// how much power am I using?
			powerDraw = powerDrawAtOnCycleTimestep(cycleSeconds, cycleSecondsElapsed, timestep);
			
			// add some noise of up to 4w in either direction
			
			try {
				powerDraw += (int)(noiseDistribution.sample());
			}
			catch (MathException e) {
				// don't care if noise is problematic
			}
			
			// don't go below 0
			powerDraw = Math.max(powerDraw, 0);
			
			// should I turn off?
			if(cycleSeconds == cycleSecondsElapsed + 1) {
				cycleSecondsElapsed = 0;
				on = false;
				if(isTraining()) {
					inTraining = false;
					
					currentOnOffPair[1] = new GenericStateTransition(-1, null, 0, false, getCurrentSimulatedTime(timestep) + 5 * 1000);
					allOnOffPairs.add(currentOnOffPair);
				}
				
				int newCycleSeconds = (int)(Math.random() * (float)maxOffPeriod());
				logger.debug("at " + cal.getTime() + " " + getName() + " turning off for " + newCycleSeconds + " + seconds");
				cycleSeconds = newCycleSeconds;

				addStateTransition(timestep, false);
			} else {
				cycleSecondsElapsed++;
			}
		}
		
		// calcuate watt-seconds = watts * 1 second
		energyConsumedDuringTimestep += (double)powerDraw;
		
		// if the timestep duration has passed (enough for a new timestamp) - create one
		if((timestep + 1) % TIMESTEP_DURATION == 0) {
			if(energyConsumedDuringTimestep > 0) {
				EnergyTimestep currentTimestep = new EnergyTimestep();
				currentTimestep.setEnergyConsumed(energyConsumedDuringTimestep);
				
				Calendar startTime = new GregorianCalendar();
				startTime.setTime(simulation.getStartTime());
				startTime.add(Calendar.SECOND, timestep + 1 - TIMESTEP_DURATION);
				
				currentTimestep.setStartTime(startTime.getTime());
				
				Calendar endTime = (Calendar)startTime.clone();
				endTime.add(Calendar.SECOND, TIMESTEP_DURATION - 1);
				
				currentTimestep.setEndTime(endTime.getTime());
				energyTimesteps.add(currentTimestep);
			}
			
			energyConsumedDuringTimestep = 0;
		}
		
		return powerDraw;
	}
	
	private long getCurrentSimulatedTime(int timestep) {
		Calendar cal = new GregorianCalendar();
		cal.setTime(simulation.getStartTime());
		cal.add(Calendar.SECOND, timestep);
		
		return cal.getTimeInMillis();
	}

	public List<ApplianceStateTransition> getApplianceOnOffs() {
		// take only the requested number of state transitions
		List<ApplianceStateTransition> onOffs = new ArrayList<ApplianceStateTransition>();
		
		Set<Integer> numbers = new HashSet<Integer>();
		
		// get two of the on/off pairs randomly while there are still more on/off pairs and we haven't picked enough of them
		while(numbers.size() < allOnOffPairs.size() && numbers.size() < simulation.getLabelsPerOnOff()) {
			int number = (int)(Math.random() * (float)allOnOffPairs.size());
			numbers.add(number);
		};
		
		// fill the list
		for(Integer number : numbers) {
			onOffs.addAll(Arrays.asList(allOnOffPairs.get(number)));
		}
		
		return onOffs;
	}
	
	public List<ApplianceStateTransition> getAllApplianceStateTransitions() {
		return allStateTransitions;
	}
	
	public void setAllStateTransitions(List<ApplianceStateTransition> allStateTransitions) {
		this.allStateTransitions = allStateTransitions;
	}

	public final UserAppliance getLabeledAppliance() {
		return labeledAppliance;
	}
	
	public final List<EnergyTimestep> getEnergyTimesteps() {
		return energyTimesteps;
	}

	public void setEnergyTimesteps(List<EnergyTimestep> energyTimesteps) {
		this.energyTimesteps = energyTimesteps;
	}

	public void setLabeledAppliance(UserAppliance labeledAppliance) {
		this.labeledAppliance = labeledAppliance;
	}
	
	public Appliance getAppliance() {
		return appliance;
	}

	public boolean isTraining() {
		return inTraining;
	}	
}
