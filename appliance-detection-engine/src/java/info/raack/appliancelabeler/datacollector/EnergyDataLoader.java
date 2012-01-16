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
package info.raack.appliancelabeler.datacollector;

import info.raack.appliancedetection.common.email.EmailSender;
import info.raack.appliancedetection.common.service.ErrorService;
import info.raack.appliancedetection.common.service.ErrorService.URGENCY;
import info.raack.appliancelabeler.data.Database;
import info.raack.appliancelabeler.machinelearning.ApplianceDetectionManager;
import info.raack.appliancelabeler.model.AlgorithmPredictions;
import info.raack.appliancelabeler.model.energymonitor.EnergyMonitor;
import info.raack.appliancelabeler.security.OAuthData;
import info.raack.appliancelabeler.service.DataService;
import info.raack.appliancelabeler.util.OAuthUnauthorizedException;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth.consumer.token.OAuthConsumerToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

/**
 * Grabs energy data from the wired configuration loaders and stores it in a dedicated database for fast processing.
 * 
 * @author Taylor Raack
 *
 */

@Component
public class EnergyDataLoader implements Runnable {
	private Logger logger = LoggerFactory.getLogger(EnergyDataLoader.class);
	
	@Autowired
	private DataService dataService;
	
	@Autowired
	private Database database;
	
	@Autowired
	private List<ConfigurationLoader<EnergyMonitor>> configurationLoaders;
	
	@Value("${active.loaders}")
	private String activeLoaders;
	
	private Collection<String> loaderNames;
	
	@Autowired
	private ErrorService errorService;
	
	@Autowired
	private EmailSender emailSender;
	
	@Value("${energylabeler.url}")
	private String energylabelerUrl;
	
	@Value("${energypolling.frequency}")
	private int energyPollingFrequency;
	/*@Autowired
	private BackupDatabase backupDatabase;*/
	
	private Date lastSuccessfulReload = new Date();
	
	private int reloadErrorEmailThresholdMinutes = 180;
	
	private ScheduledExecutorService scheduler;
	
	@PostConstruct
	public void init() {
		loaderNames = StringUtils.commaDelimitedListToSet(activeLoaders);

		startRefreshTimer();
	}

	private void startRefreshTimer() {
		try {
			logger.info("Starting energy data loader with polling frequency of " + energyPollingFrequency);
	
			scheduler = Executors.newScheduledThreadPool(1);
			scheduler.scheduleWithFixedDelay(this, 0, energyPollingFrequency, TimeUnit.SECONDS);
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
			logger.info("Stopping energy data loader...");
			scheduler.shutdown();
		} catch (Exception e) {
			logger.warn("Could not shutdown quartz cleanly", e);
		}
	}
	
	public void run() {
		logger.debug("Loading new energy data from monitors...");
		for(ConfigurationLoader<EnergyMonitor> loader : configurationLoaders) {
			logger.debug("Checking if we need to run " + loader.getClass().getName());
			String className = loader.getClass().getName();
			logger.debug("Checking " + className);
			for(String loaderName : loaderNames) {
				if(className.contains(loaderName)) {
					Collection<EnergyMonitor> energyMonitors = loader.getActiveEnergyMonitors();
					for(EnergyMonitor energyMonitor : energyMonitors) {
						try {
							EnergyMonitor dbMonitor = database.getEnergyMonitor(energyMonitor.getUserId(), energyMonitor.getMonitorId(), energyMonitor.getType());
							getDataForEnergyMonitor(dbMonitor != null ? dbMonitor : energyMonitor);
							lastSuccessfulReload = new Date();
						} catch (Exception e) {
							if(new Date().getTime() - lastSuccessfulReload.getTime() > reloadErrorEmailThresholdMinutes * 60000) {
								// send email about this configuration to stepgreen admin with latest error
								errorService.reportError("Could not communicate with Stepgreen service for " + reloadErrorEmailThresholdMinutes + " minutes", URGENCY.REGULAR, e);
								lastSuccessfulReload = new Date();
							}
							logger.warn("Could not get data from energy monitor " + energyMonitor, e);
						}
					}
					break;
				}
			}
		}
	}

	/*private AggregatedTeds reloadConfigurationsFromBackupDatabase() {
		return backupDatabase.getConfigurationsFromBackupDatabase();
	}*/

	private void getDataForEnergyMonitor(final EnergyMonitor energyMonitor) {
		
		energyMonitor.pollLock();
		
		try {
			logger.debug("Getting data for " + energyMonitor);
			long lastDatapoint = dataService.getLastDatapoint(energyMonitor);
	
			if(lastDatapoint == -1) {
				// we don't know what the latest data point that was saved was - grab it from the database
				logger.debug("No latest datapoint returned for " + energyMonitor + "; requesting all datapoints");
			}
			
			// get data from stepgreen service
			while(true) {
				
				// split up into chunks
				synchronized(this) {
					// construct oauth access tokens
					OAuthData oAuthData = dataService.getOAuthDataForUserId(energyMonitor.getUserId());
					List<SecondData> data = null;
					try {
						data = energyMonitor.getSecondData(oAuthData.getRequestProcessor(), oAuthData.getSecurityContext(), lastDatapoint + 1, 10000L);
					} catch (OAuthUnauthorizedException e) {
						String email = database.getUserEmailForUserId(energyMonitor.getUserId());
						logger.warn("Could not get list of TEDS for user " + email + " - request through oauth is unauthorized");
						emailSender.sendGeneralEmail(email, "Your Stepgreen energy consumption authorization is no longer valid", 
								"At some point in the past, you used the Stepgreen Appliance Energy Visualization tool to see detailed usage of your appliance energy use.\n\n" +
								"We have become unable to access your Stepgreen energy data. If you wish to continue using the tool, please visit " + energylabelerUrl + " now. " +
								"We will be unable to collect and provide any more energy predictions until you do so.\n\n" +
								"Thanks,\n" +
								"The Stepgreen Appliance Energy Team");
						
						// now invalidate the tokens
						Map<String,OAuthConsumerToken> tokenMap = new HashMap<String,OAuthConsumerToken>();
						dataService.saveOAuthTokensForUserId(energyMonitor.getUserId(), email, tokenMap);
						break;
					}
				
					int returnedMeasurements = data.size();
					
					if(returnedMeasurements == 0) {
						logger.debug("No measurements returned from " + energyMonitor + "; stopping polling");
						break;
					}
					
					removeDuplicates(data);
					
					// sort data
					Collections.sort(data, new Comparator<SecondData>() {
						public int compare(SecondData o1, SecondData o2) {
							return (int)(o1.getCalLong() - o2.getCalLong());
						}});
					
					Map<Integer, AlgorithmPredictions> allAlgorithmPredictions = new HashMap<Integer, AlgorithmPredictions>();
					
					dataService.storeDataAndAlgorithmPredictions(energyMonitor, data, lastDatapoint + returnedMeasurements, allAlgorithmPredictions);
					
					if(returnedMeasurements < 10000) {
						logger.debug("Less than 10000 datapoints returned (" + data.size() + " unique measurements); stopping polling");
						break;
					}
					
					lastDatapoint += returnedMeasurements;
				}
			}
		} finally {
			energyMonitor.pollUnlock();
		}
	}

	private void removeDuplicates(List<SecondData> data) {
		// remove any duplicates
		Map<Long, SecondData> singleDatas = new HashMap<Long,SecondData>();
		for(SecondData point : data) {
			singleDatas.put(point.getCalLong(), point);
		}
		
		data.clear();
		data.addAll(singleDatas.values());
	}

	/*private List<SecondData> getSecondDataFromBackupDatabase(String userId, Ted5000 ted, Mtu mtu, String datapointsBack) {
		return backupDatabase.getSecondDataFromBackupDatabase(userId, ted, mtu, datapointsBack);
	}*/

	

	
}
