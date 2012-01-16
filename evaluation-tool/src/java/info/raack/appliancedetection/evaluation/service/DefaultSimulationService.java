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
package info.raack.appliancedetection.evaluation.service;

import info.raack.appliancedetection.common.service.ErrorService;
import info.raack.appliancedetection.common.service.ErrorService.URGENCY;
import info.raack.appliancedetection.common.util.DateUtils;
import info.raack.appliancedetection.evaluation.data.Database;
import info.raack.appliancedetection.evaluation.model.Evaluation;
import info.raack.appliancedetection.evaluation.model.EvaluationGroup;
import info.raack.appliancedetection.evaluation.model.Simulation;
import info.raack.appliancedetection.evaluation.model.SimulationGroup;
import info.raack.appliancedetection.evaluation.model.appliance.SimulatedAppliance;
import info.raack.appliancelabeler.datacollector.SimulatedTED5000ConfigurationLoader;
import info.raack.appliancelabeler.machinelearning.ApplianceDetectionManager;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.ApplianceEnergyConsumptionDetectionAlgorithm;
import info.raack.appliancelabeler.model.Appliance;
import info.raack.appliancelabeler.model.EnergyTimestep;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.energymonitor.EnergyMonitor;
import info.raack.appliancelabeler.model.energymonitor.SimulatedEnergyMonitor;
import info.raack.appliancelabeler.service.DataService;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DefaultSimulationService implements SimulationService, Runnable {
	
	private Logger logger = LoggerFactory.getLogger(DefaultSimulationService.class);
	
	@Autowired
	private SimulatedTED5000ConfigurationLoader configurationLoader;
	
	@Autowired
	private List<SimulatedAppliance> possibleAppliances;
	
	@Autowired
	private Database evaluationDatabase;
	
	@Autowired
	private info.raack.appliancelabeler.data.Database engineDatabase;
	
	@Autowired
	private DataService dataService;
	
	@Autowired
	private ErrorService errorService;
	
	@Autowired
	private DateUtils dateUtils;

	@Autowired
	private ApplianceDetectionManager applianceDetectionManager;
	
	private ScheduledExecutorService scheduler;
	
	@Autowired
	private List<ApplianceEnergyConsumptionDetectionAlgorithm> algorithms;
	
	@PostConstruct
	public void init() {
		startRefreshTimer();
	}
	
	private void startRefreshTimer() {
		try {
			logger.info("Starting Simulation Service monitor...");
	
			scheduler = Executors.newScheduledThreadPool(1);
			scheduler.scheduleWithFixedDelay(this, 0, 1, TimeUnit.SECONDS);
		} catch (Exception e) {
			logger.error("Could not schedule TED data loader", e);
		}
	}
	
	@PreDestroy
	public void stop() {
		stopRefreshTimer();
	}
	
	private void stopRefreshTimer() {
		try {
			logger.info("Stopping Simulation Service monitor...");
			scheduler.shutdown();
		} catch (Exception e) {
			logger.warn("Could not shutdown cleanly", e);
		}
	}

	public String startNewSimulation(long durationInSeconds, int labelsPerOnOff, int onConcurrency, int numAppliances, SimulationGroup group) {
		// start the simulation off in the past by durationInSeconds seconds
		Calendar cal = new GregorianCalendar();
		cal.add(Calendar.SECOND, -1 * (int)durationInSeconds);
		
		Simulation simulation = new Simulation(dateUtils, cal.getTime(), durationInSeconds, numAppliances, labelsPerOnOff, onConcurrency, possibleAppliances, group);
		
		// create new simulated energy monitor
		SimulatedEnergyMonitor monitor = new SimulatedEnergyMonitor(simulation);
		
		// generate simulated training set with labels.
		generateAndStoreSimulatedTrainingDataSet(monitor);
		
		// activate energy monitor
		configurationLoader.activateEnergyMonitor(monitor);
		

		logger.debug("Creating new simulated energy monitor " + monitor.getMonitorId());
		
		return simulation.getId();
	}

	public void run() {
		// look for simulations whose data has already been captured
		for(final SimulatedEnergyMonitor monitor : configurationLoader.getActiveEnergyMonitors()) {
			if(monitor.isEnergyFinishedBeingRequested()) {
				try {
					configurationLoader.deactivateEnergyMonitor(monitor);
					

					// now train the system
					logger.info("Forcing immediate training for simulation " + monitor.getSimulation().getId());
					applianceDetectionManager.trainPredictionModelsForMonitors(new ArrayList<EnergyMonitor>() {{ add(monitor);}});
					
					if(monitor.isFinalRun() == false) {
						// the last fill was sending testing data into the system
						
						// now that the training data for this monitor has been inputted into the system, generate actual simulated data for testing
						
						monitor.getSimulation().reset();
						monitor.getSimulation().incrementTimeForTestDataSet();
						monitor.getSimulation().run();
						
						// save simulation info - save simulation
						evaluationDatabase.saveSimulation(monitor.getSimulation());
						
						monitor.setFinalRun(true);
						
						// activate energy monitor
						monitor.reset();
						
						configurationLoader.activateEnergyMonitor(monitor);
					} else {
						logger.info("Simulation for " + monitor + " complete.");
						evaluationDatabase.setDone(monitor.getSimulation());
					}
					
				} catch (Exception e) {
					logger.error("Could not close simulation", e);
					errorService.reportError("Could not close simulation", URGENCY.REGULAR, e);
				}
			}
		}
	}
	
	private void generateAndStoreSimulatedTrainingDataSet(SimulatedEnergyMonitor energyMonitor) {
		
		
		Simulation simulation = energyMonitor.getSimulation();
		
		// run the simulation
		simulation.run();
		
		Map<SimulatedAppliance, List<ApplianceStateTransition>> applianceTransitions = simulation.getApplianceOnOffs();
		
		
		for(SimulatedAppliance simulatedAppliance : applianceTransitions.keySet()) {
			List<ApplianceStateTransition> transitions = applianceTransitions.get(simulatedAppliance);
			
			if(transitions != null && transitions.size() > 0) {
				logger.debug("Creating new appliance for " + simulatedAppliance.toString());
				
				Appliance appliance = engineDatabase.getAllAppliances().get(0);
				
				// TODO - don't just pick the first appliance, as the actual appliance type might become important to any algorithm later
				UserAppliance userAppliance = new UserAppliance(-1, appliance, simulatedAppliance.getName(), -1, false);
				
				engineDatabase.addUserAppliance(energyMonitor, userAppliance);
				
				// link the labeled user appliance to the simulated appliance
				simulatedAppliance.setLabeledAppliance(userAppliance);
				
				logger.debug("Labeling transitions for " + appliance);
				
				// need to set the appliance for the state transitions - we didn't have them before, so they could not go into the ApplianceStateTransition constructor
				for(ApplianceStateTransition transition : transitions) {
					transition.setUserAppliance(userAppliance);
				}
				
				engineDatabase.storeUserOnOffLabels(transitions);
			} else {
				logger.debug("No labels for " + simulatedAppliance);
			}
		}
		
		
	}

	public List<Simulation> getAllSimulationInformation() {
		return evaluationDatabase.getAllSimulationInformation();
	}

	public Evaluation getEvaluation(int algorithmId, String simulationId, Date start, Date end, boolean includeRawPowerMeasurements) {
		// get simulation
		
		Date queryStart = null;
		Date queryEnd = null;
		
		if(start != null && end != null && end.getTime() - start.getTime() < 5 * 60 * 1000) {
			// if less than a five minute increment is selected, just use it
			queryStart = start;
			queryEnd = end;
		}
		else {
			if(start != null) {
				Calendar cal = new GregorianCalendar();
				cal.setTime(start);
				queryStart = dateUtils.getPreviousFiveMinuteIncrement(cal).getTime();
			}
			
			if(end != null) {
				Calendar cal = new GregorianCalendar();
				cal.setTime(end);
				queryEnd = dateUtils.getNextFiveMinuteIncrement(cal).getTime();
			}
		}
		
		logger.debug("Time window for evaluation query: " + queryStart + " - " + queryEnd);
		Simulation simulation = evaluationDatabase.getSimulation(simulationId, queryStart, queryEnd, includeRawPowerMeasurements);
		
		
		EnergyMonitor databaseMonitor = evaluationDatabase.getSimulatedEnergyMonitor(simulation.getId());
		SimulatedEnergyMonitor em = new SimulatedEnergyMonitor(simulation);
		em.setId(databaseMonitor.getId());
		
		// get predicted energy measurements
		Map<UserAppliance, List<EnergyTimestep>> predictedEnergyUsage = dataService.getApplianceEnergyConsumptionForMonitor(em, algorithmId, simulation.getStartTime(), simulation.getEndTime());
		
		// get predicted appliance state transitions
		Map<UserAppliance, List<ApplianceStateTransition>> predictedApplianceStateTransitions = dataService.getPredictedApplianceStateTransitionsForMonitor(em, algorithmId, simulation.getStartTime(), simulation.getEndTime());
		
		return new Evaluation(simulation, predictedEnergyUsage, predictedApplianceStateTransitions);
	}

	@Override
	public int startNewSimulationGroup(final int numberOfSimulations, final int durationInSeconds, final int onOffLabelsPerAppliance, final int onConcurrency, final int numAppliances) {
		final SimulationGroup group = new SimulationGroup(numberOfSimulations, new Date(), durationInSeconds, numAppliances, onConcurrency, onOffLabelsPerAppliance);
		
		evaluationDatabase.saveSimulationGroup(group);
		
		Runnable simulationGroupRunner = new Runnable() {

			public void run() {
				try {
					for(int i = 0; i < numberOfSimulations; i++) {
						// start the simulation
						String simulationId = startNewSimulation(durationInSeconds, onOffLabelsPerAppliance, onConcurrency, numAppliances, group);
						
						// wait until the simulation is done
						while(true) {
							// sleep for 10 seconds
							try {
								Thread.sleep(10000);
							}
							catch (InterruptedException e) {
								// don't care if this thread got interrupted
							}
							
							if(evaluationDatabase.isSimulationDone(simulationId)) {
								break;
							}
						}
					}
					logger.info("Done with simulation group " + group);
				} catch (Exception e) {
					logger.error("Could not finish simulation group", e);
				}
			}
		};
		
		// not going to try to stop this thread if we shut down the container, I might write that later for completeness
		new Thread(simulationGroupRunner).start();

		return group.getId();
	}

	@Override
	public EvaluationGroup getEvaluationGroup(int simulationGroupId) {
		// get all simulation info for simulation group
		List<Simulation> simulations = evaluationDatabase.getAllSimulationInformationForGroup(simulationGroupId);
		
		// get each evaluation, for all algorithms
		Map<ApplianceEnergyConsumptionDetectionAlgorithm, List<Evaluation>> evaluationInfo = new HashMap<ApplianceEnergyConsumptionDetectionAlgorithm, List<Evaluation>>();
		
		for(ApplianceEnergyConsumptionDetectionAlgorithm algorithm : algorithms) {
			List<Evaluation> evaluations = new ArrayList<Evaluation>();
			
			for(Simulation simulation : simulations) {
				Evaluation evaluation = getEvaluation(algorithm.getId(), simulation.getId(), null, null, true);
				evaluation.dumpInternalData();
				evaluation.getSimulation().reset();
				evaluations.add(evaluation);
			}
			
			evaluationInfo.put(algorithm, evaluations);
		}
		
		return new EvaluationGroup(evaluationDatabase.getSimulationGroup(simulationGroupId), evaluationInfo);
	}
}
