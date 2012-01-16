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
package info.raack.appliancelabeler.machinelearning;

import info.raack.appliancedetection.common.service.ErrorService;
import info.raack.appliancedetection.common.service.ErrorService.URGENCY;
import info.raack.appliancedetection.common.util.DateUtils;
import info.raack.appliancelabeler.data.Database;
import info.raack.appliancelabeler.data.batch.ItemReader;
import info.raack.appliancelabeler.data.batch.ListItemReader;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.AlgorithmResult;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.ApplianceEnergyConsumptionDetectionAlgorithm;
import info.raack.appliancelabeler.model.AlgorithmPredictions;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.energymonitor.EnergyMonitor;
import info.raack.appliancelabeler.service.DataService.LabelResult;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

@Component
public class DefaultApplianceDetectionManager implements ApplianceDetectionManager, Runnable {
	private Logger logger = LoggerFactory.getLogger(DefaultApplianceDetectionManager.class);
	
	@Autowired
	private List<ApplianceEnergyConsumptionDetectionAlgorithm> applianceStateTransitionDetectionAlgorithms;
	
	@Autowired
	private Database database;
	
	@Autowired
	private DateUtils dateUtils;
	
	@Autowired
	private ErrorService errorService;
	
	@Value("${environment}")
	private String env;
	
	@Value("${active.algorithms}")
	private String activeAlgorithms;
	
	@Value("${automatic.model.retraining}")
	private boolean autoModelRetraining;
	
	@Value("${automatic.model.retraining.interval}")
	private int retrainingInterval;
	
	private ExecutorService executorService;
	
	private ScheduledExecutorService scheduler;
	
	private Map<Integer, Date> lastTimeIncludedInTraining = new HashMap<Integer, Date>();
	
	@PostConstruct
	public void init() {
		
		// construct executor service as a fixed size thread pool with a max size calculated using the number of  number of threads available to this runtime minus one and the max heap size
		int maxThreadsForProcessors = Math.max(Runtime.getRuntime().availableProcessors() / 2, 1);
		logger.info("Max processors: " + Runtime.getRuntime().availableProcessors() + "; choosing " + maxThreadsForProcessors + " for max processing threads");
		
		long maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024;
		int maxThreadsForMemory = Math.max((int)maxMemory / 500, 1);
		logger.info("Max free memory: " + maxMemory + "M; choosing " + maxThreadsForMemory + " for max processing threads");
		
		if(!env.equals("junit") && System.getProperty("noMinHeap") == null && maxMemory < 500) {
			throw new RuntimeException("Heap free memory of " + maxMemory + " less than minimum requirement of 500M");
		}
		
		//executorService = Executors.newFixedThreadPool();
		executorService = Executors.newFixedThreadPool(Math.min(maxThreadsForProcessors, maxThreadsForMemory));
		
		startDailyRetrainingThread();
	}
	
	private void startDailyRetrainingThread() {
		try {
			if(autoModelRetraining) {
				logger.info("Starting daily retraining thread...");
		
				scheduler = Executors.newScheduledThreadPool(1);
				scheduler.scheduleWithFixedDelay(this, 0, retrainingInterval, TimeUnit.HOURS);
			}
		} catch (Exception e) {
			logger.error("Could not schedule daily retraining thread", e);
		}
	}
	
	@PreDestroy
	public void stop() {
		stopDailyRetrainingThread();
		executorService.shutdown();
	}

	private void stopDailyRetrainingThread() {
		try {
			if(autoModelRetraining) {
				logger.info("Stopping daily retraining thread...");
				scheduler.shutdown();
			}
		} catch (Exception e) {
			logger.warn("Could not shutdown daily retraining thread", e);
		}
	}
	
	@Override
	public void run() {
		try {
			train();
		} catch (Exception e) {
			logger.error("Could not train models", e);
			errorService.reportError("Could not train models", URGENCY.REGULAR, e);
		}
	}

	public void train() {
		logger.info("Running daily retraining...");
		trainPredictionModelsForMonitors(database.getEnergyMonitors());
		logger.info("Done with daily retraining.");
	}
	
	public void trainPredictionModelsForMonitors(List<EnergyMonitor> energyMonitors) {
		
		// get a year worth of data to train upon
		
		List<Callable<Integer>> tasks = new ArrayList<Callable<Integer>>();
		
		// for each energy monitor
		for(final EnergyMonitor monitor : energyMonitors) {
			monitor.pollLock();
			logger.info("Starting training for " + monitor);
			
			try {
				
				// do training for model
				for(final ApplianceEnergyConsumptionDetectionAlgorithm algorithm : applianceStateTransitionDetectionAlgorithms) {
					String className = algorithm.getAlgorithmName();
					if(activeAlgorithms.contains(className)) {
						
						Callable<Integer> task = new Callable<Integer>() {

							@Override
							public Integer call() {
								// purge current energy predictions and appliance state transitions
								logger.debug("Removing previous state transition and energy predictions for " + monitor + "; " + algorithm);
								database.removeStateTransitionAndEnergyPredictionsForAlgorithmAndMonitor(algorithm, monitor);

								logger.info("Training " + algorithm);
								ItemReader<SecondData> dataReader = null;
								try {
									//Calendar now = new GregorianCalendar();
									
									Date lastMeasurementTime = database.getLastMeasurementTimeForEnergyMonitor(monitor);
									
									if(lastMeasurementTime == null) {
										logger.debug("No data points for monitor " + monitor + "; not doing any retraining");
										return null;
									}
									
									Calendar finalMeasurementTime = new GregorianCalendar();
									finalMeasurementTime.setTimeInMillis(lastMeasurementTime.getTime());

									finalMeasurementTime = dateUtils.getPreviousFiveMinuteIncrement(finalMeasurementTime); 
									
									// now go one year backward from the last measurement
									Calendar first = (Calendar)finalMeasurementTime.clone();
									first.add(Calendar.YEAR, -1);
									
									long measurements = (finalMeasurementTime.getTimeInMillis() - first.getTimeInMillis()) / 1000;
									
									logger.info("Training algorithms with data from " + first.getTime() + " to " + finalMeasurementTime.getTime());
									
									dataReader = database.getEnergyMeasurementReaderForMonitor(monitor, first.getTime(), finalMeasurementTime.getTime(), (int)measurements);
								
									// 1) generate a model to create predictions
									AlgorithmResult result = algorithm.train(monitor, dataReader);
									
									// 2) save the model
									if(result != null) {
										logger.debug("Saving algorithm model for " + result.getAlgorithm());
										database.saveAlgorithmResult(result);
									}
									
									// 3) use the model to generate predicted state transitions and energy predictions
									logger.info("Re-predicting appliance state transitions and energy consumption for " + algorithm);

									// move the final time back to the last five minute boundary
									dataReader.moveToBeginning();
									
									final AlgorithmPredictions algorithmPredictions = algorithm.calculateApplianceEnergyUsePredictions(monitor, first, finalMeasurementTime, dataReader);
									
									// 4) save the predictions
									database.storeAlgorithmPredictions(monitor, new HashMap<Integer,AlgorithmPredictions>() {{ put(algorithm.getId(), algorithmPredictions); }});
									
									lastTimeIncludedInTraining.put(monitor.getId(), lastMeasurementTime);
									logger.info("Done training " + monitor + " with " + algorithm);
									
								} catch (Exception e) {
									logger.error("Could not train models", e);
									errorService.reportError("Could not train models", URGENCY.REGULAR, e);
								} finally {
									if(dataReader != null) {
										dataReader.close();
									}
								}
								return null;
							}};
							tasks.add(task);
					}
				}
			} finally {
				monitor.pollUnlock();
			}
		}
		
		try {
			executorService.invokeAll(tasks);
		}
		catch (InterruptedException e) {
			throw new RuntimeException("Retraining executor was interrupted", e);
		}
		logger.info("Done with all retraining");
	}
	
	
	public Map<Integer, AlgorithmPredictions> findAlgorithmPredictionsForAlgorithms(EnergyMonitor monitor, List<SecondData> measurements) {
		logger.debug("Generating algorithm energy predictions for new measurements");
		return findAlgorithmPredictionsForAlgorithms(monitor, Collections.<Integer>emptyList(), measurements);
	}
	
	private Map<Integer, AlgorithmPredictions> findAlgorithmPredictionsForAlgorithms(EnergyMonitor energyMonitor, List<Integer> ignoredAlgorithmIds, final List<SecondData> measurements) {
		Map<Integer, AlgorithmPredictions> allAlgorithmPredictions = new HashMap<Integer, AlgorithmPredictions>();
		
		for(ApplianceEnergyConsumptionDetectionAlgorithm algorithm : applianceStateTransitionDetectionAlgorithms) {
			if(!ignoredAlgorithmIds.contains(algorithm.getId()) && activeAlgorithms.contains(algorithm.getClass().getName())) {
				// backfill
				logger.info("Running " + algorithm);
				
				Calendar first = measurements.get(0).getCal();
				Calendar last = measurements.get(measurements.size() - 1).getCal();
				allAlgorithmPredictions.put(algorithm.getId(), algorithm.calculateApplianceEnergyUsePredictions(energyMonitor, first, last, new ListItemReader<SecondData>(measurements)));
			}
		}
		
		return allAlgorithmPredictions;
	}

	@Override
	public Date getLastTimeIncludedInTraining(EnergyMonitor monitor) {
		return lastTimeIncludedInTraining.get(monitor.getId());
	}

	@Override
	public int getRetrainingIntervalInHours() {
		return retrainingInterval;
	}

	@Override
	public Map<UserAppliance, Integer> getAdditionalTrainingsRequiredPerUserAppliance(EnergyMonitor energyMonitor) {
		// get number of trainings currently in database - if it's less than 1, then we need one training
		// this is not smart at all, but it works
		
		List<UserAppliance> userAppliances = database.getUserAppliancesFromUserForEnergyMonitor(energyMonitor);
		List<ApplianceStateTransition> transitions = database.getUserOnOffLabels(energyMonitor);
		
		Map<UserAppliance, Integer> trainings = new HashMap<UserAppliance, Integer>();
		
		// just assume one training is necessary for now
		for(UserAppliance appliance : userAppliances) {
			trainings.put(appliance, 1);
		}
		
		// mark each appliance with a training
		for(ApplianceStateTransition transition : transitions) {
			trainings.put(transition.getUserAppliance(),0);
		}
		
		return trainings;
	}

	@Override
	public LabelResult detectAcceptableUserTraining(EnergyMonitor energyMonitor, Date startTedDate, Date endTedDate) {
		// get data reader
		long measurements = (endTedDate.getTime() - startTedDate.getTime()) / 1000;
		ItemReader<SecondData> dataReader = database.getEnergyMeasurementReaderForMonitor(energyMonitor, startTedDate, endTedDate, (int)measurements);
		
		try {
			// look over all active algorithms and ensure that they all report OK before reporting ok
			for(final ApplianceEnergyConsumptionDetectionAlgorithm algorithm : applianceStateTransitionDetectionAlgorithms) {
				if(activeAlgorithms.contains(algorithm.getClass().getName())) {
					
					LabelResult result = algorithm.detectAcceptableUserTraining(dataReader);
					if(result != LabelResult.OK) {
						return result;
					} else {
						// reset reader to beginning of data stream
						dataReader.moveToBeginning();
					}
					
				}
			}
			
			// all results must have been ok if we got this far - return ok
			return LabelResult.OK;
		} finally {
			// make sure to close the reader at the end, no matter what
			dataReader.close();
		}
	}
}
