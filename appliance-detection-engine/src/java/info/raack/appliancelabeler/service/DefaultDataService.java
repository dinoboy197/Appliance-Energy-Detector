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
package info.raack.appliancelabeler.service;

import info.raack.appliancedetection.common.service.ErrorService;
import info.raack.appliancedetection.common.service.ErrorService.URGENCY;
import info.raack.appliancelabeler.data.Database;
import info.raack.appliancelabeler.machinelearning.ApplianceDetectionManager;
import info.raack.appliancelabeler.model.AlgorithmPredictions;
import info.raack.appliancelabeler.model.EnergyTimestep;
import info.raack.appliancelabeler.model.StepgreenUserDetails;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.GenericStateTransition;
import info.raack.appliancelabeler.model.energymonitor.EnergyMonitor;
import info.raack.appliancelabeler.security.OAuthData;
import info.raack.appliancelabeler.util.OAuthUnauthorizedException;
import info.raack.appliancelabeler.util.ResponseHandler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.bind.JAXBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth.consumer.OAuthSecurityContext;
import org.springframework.security.oauth.consumer.OAuthSecurityContextImpl;
import org.springframework.security.oauth.consumer.token.OAuthConsumerToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.SAXException;

import edu.cmu.hcii.stepgreen.data.base.StepgreenUser;
import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;
import edu.cmu.hcii.stepgreen.data.teds.AggregatedTeds;
import edu.cmu.hcii.stepgreen.data.teds.Ted5000;

@Component
public class DefaultDataService implements DataService {
	
	private Logger logger = LoggerFactory.getLogger(DefaultDataService.class);

	private String tedListURLPattern = "http://%s/api/v1/users/%s/ted5000s.xml";

	private String userInfoUri = "http://%s/api/v1/users/current_user.xml";

	@Value("${stepgreen.basehost}")
	private String stepgreenBasehost;
	
	@Autowired
	private OAuthRequestProcessor oAuthRequestProcessor;
	
	@Autowired
	private Database database;
	
	@Autowired
	private ErrorService errorService;
	
	@Autowired
	private ApplianceDetectionManager detectionManager;
	
	private Map<String, Long> lastDatapoints;
	
	private ScheduledExecutorService scheduler;
	
	private StepgreenUserDetailsResponseHandler stepgreenUserDetailsReponseHandler = new StepgreenUserDetailsResponseHandler();
	
	private Date lastSuccessfulReload = new Date();
	
	private int reloadErrorEmailThresholdMinutes = 180;
  
	private class ReloadUserEmailRunnable implements Runnable {
		
		@Override
		public void run() {
			try {
				reloadUserEmails();
			} catch (Exception e) {
				logger.error("Could not reload user emails",e);
				errorService.reportError("Could not reload emails", URGENCY.REGULAR, e);
			}
		}
	}
	
	@PostConstruct
	public void init() throws JAXBException, SAXException {
		lastDatapoints = new HashMap<String, Long>();
		
		// initialize email reloader daily
		scheduler = Executors.newScheduledThreadPool(1);
		scheduler.scheduleWithFixedDelay(new ReloadUserEmailRunnable(), 0, 5, TimeUnit.MINUTES);
	}
	
	@PreDestroy
	public void stop() {
		scheduler.shutdown();
	}
	
	public void reloadUserEmails() {
		// for each user in the oauth users table
		Map<String, Map<String, OAuthConsumerToken>> allTokens = database.getOAuthTokensForAllUsers();
		
		for(String userId : allTokens.keySet()) {
			try {
				
				if(allTokens.get(userId).isEmpty()) {
					// don't request anything with no tokens
					continue;
				}
				
				// get stepgreen user details
				StepgreenUserDetails details = getStepgreenUserInfo(userId);
				lastSuccessfulReload = new Date();
				
				// save it back into the database
				saveOAuthTokensForUserId(userId, details.getEmail(), allTokens.get(userId));
			} catch (Exception e) {
				logger.error("Could not load user email for " + userId, e);
				if(new Date().getTime() - lastSuccessfulReload.getTime() > reloadErrorEmailThresholdMinutes * 60000) {
					// send email about this configuration to stepgreen admin with latest error
					errorService.reportError("Could not communicate with Stepgreen service for " + reloadErrorEmailThresholdMinutes + " minutes", URGENCY.REGULAR, e);
					lastSuccessfulReload = new Date();
				}
			}
		}
	}

	public List<Ted5000> getTEDIdsForUserId(String userId, boolean needContext) {
		String requestURI = String.format(tedListURLPattern, stepgreenBasehost, userId == null ? "current_user" : userId);
		
		OAuthSecurityContext context = null;
		if(userId != null) {
			context = getOAuthDataForUserId(userId).getSecurityContext();
			if(context.getAccessTokens().isEmpty()) {
				if(needContext) {
					throw new OAuthUnauthorizedException();
				} else {
					// this context may have just been saved
					context = null;
				}
			}
		}
		
		try {
			if(context == null) {
				return oAuthRequestProcessor.processRequest(requestURI, new ResponseHandler<AggregatedTeds,List<Ted5000>>() {
					public List<Ted5000> extractValue(AggregatedTeds aggteds) {
						return removeUnfetchableTeds(aggteds);
					}
				});
			} else {
				return oAuthRequestProcessor.processRequest(requestURI, context, new ResponseHandler<AggregatedTeds,List<Ted5000>>() {
					public List<Ted5000> extractValue(AggregatedTeds ted) {
						return removeUnfetchableTeds(ted);
					}
				});
			}
		} catch (Exception e) {
			throw new RuntimeException("Could not get energy monitor information for user id " + userId, e);
		}
	}

	public StepgreenUserDetails getStepgreenUserInfo() {
		return getStepgreenUserInfo(null);
	}
	
	public StepgreenUserDetails getStepgreenUserInfo(String userId) {
		String requestURI = String.format(userInfoUri, stepgreenBasehost);
		
		if(userId == null) {
			return oAuthRequestProcessor.processRequest(requestURI, stepgreenUserDetailsReponseHandler);
		} else {
			OAuthData data = getOAuthDataForUserId(userId);
			return oAuthRequestProcessor.processRequest(requestURI, data.getSecurityContext(), stepgreenUserDetailsReponseHandler);
		}
	}
	
	public long getLastDatapoint(EnergyMonitor energyMonitor) {
		
		if(lastDatapoints.containsKey(energyMonitor.toString())) {
			logger.debug("Last datapoint for " + energyMonitor + " found in memory");
			return lastDatapoints.get(energyMonitor.toString());
		} else {
			// don't have the last datapoint in memory - grab it from the database
			logger.debug("Last datapoint for " + energyMonitor + " not found in memory; retrieving from database");
			Long lastDatapoint = database.getLastDatapoint(energyMonitor);
			lastDatapoints.put(energyMonitor.toString(), lastDatapoint);
			return lastDatapoint;
		}
	}

	
	@Transactional // ensures that the database calls in this method are executed in one transaction together
	public void storeDataAndAlgorithmPredictions(EnergyMonitor energyMonitor, List<SecondData> data, long lastOffset, Map<Integer, AlgorithmPredictions> algorithmPredictions) {
		// only store the new data points so that duplicate data points do not get pushed into database
		
		if(data == null) {
			logger.debug("No data points for " + energyMonitor);
			return;
		}
		
		if(data.size() > 0) {
			
			// send any other datapoints to the database to store
			database.storeData(energyMonitor, data, lastOffset);
			
			database.storeAlgorithmPredictions(energyMonitor, algorithmPredictions);
			
			logger.debug("Saving last datapoint offset for " + energyMonitor + ": " + lastOffset);
			lastDatapoints.put(energyMonitor.toString(), lastOffset);
			
			
		} else {
			logger.debug("No new datapoints to save for " + energyMonitor);
		}
	}

	public Map<String, OAuthConsumerToken> getOAuthTokensForUserId(String userId) {
		Map<String,OAuthConsumerToken> map = database.getOAuthTokensForUserId(userId);
		if(map != null) {
			return map;
		} else {
			return new HashMap<String,OAuthConsumerToken>();
		}
	}

	public void saveOAuthTokensForUserId(String userId, String email, Map<String, OAuthConsumerToken> tokens) {
		try {
			database.saveOAuthTokensForUserId(userId, email, tokens);
		} catch (RuntimeException e) {
			logger.error("Could not save oauth tokens for " + userId, e);
			throw e;
		}
	}

	@Transactional
	public void runInTransaction(Runnable runnable) {
		runnable.run();
	}
	
	public Map<UserAppliance, List<EnergyTimestep>> getApplianceEnergyConsumptionForMonitor(EnergyMonitor energyMonitor, int algorithmId, Date start, Date end) {

		List<EnergyTimestep> energyTimesteps = database.getApplianceEnergyConsumptionForMonitor(energyMonitor, algorithmId, start, end);
		
		Map<UserAppliance, List<EnergyTimestep>> results = new HashMap<UserAppliance, List<EnergyTimestep>>();
		
		for(EnergyTimestep energyTimestep : energyTimesteps) {
			// add new user appliance energy timestep list if it doesn't exist yet
			if(!results.containsKey(energyTimestep.getUserAppliance())) {
				results.put(energyTimestep.getUserAppliance(), new ArrayList<EnergyTimestep>());
			}
			
			results.get(energyTimestep.getUserAppliance()).add(energyTimestep);
		}
		
		return results;
	}
	
	public Map<UserAppliance, List<ApplianceStateTransition>> getPredictedApplianceStateTransitionsForMonitor(EnergyMonitor energyMonitor, int algorithmId, Date start, Date end) {

		List<ApplianceStateTransition> stateTransitions = database.getPredictedApplianceStateTransitionsForMonitor(energyMonitor, algorithmId, start, end, false);
		
		Map<UserAppliance, List<ApplianceStateTransition>> results = new HashMap<UserAppliance, List<ApplianceStateTransition>>();
		
		for(ApplianceStateTransition stateTransition : stateTransitions) {
			// add new user appliance energy timestep list if it doesn't exist yet
			if(!results.containsKey(stateTransition.getUserAppliance())) {
				results.put(stateTransition.getUserAppliance(), new ArrayList<ApplianceStateTransition>());
			}
			
			results.get(stateTransition.getUserAppliance()).add(stateTransition);
		}
		
		return results;
	}

	public LabelResult createUserGeneratedLabelsForUserApplianceId(EnergyMonitor monitor, int userApplianceId, Date labelOnTime, Date labelOffTime, boolean force) {
		// create on and off label for the user appliance with the given user appliance id
		
		// need to make sure that the on label time corresponds to the perceived time of the energy monitor data, which might not be "server" time
		
		logger.info("Attempting to detect state transitions for user label times of " + labelOnTime + " and " + labelOffTime);
		// get current energy monitor time
		Date currentTedDate = monitor.getCurrentMonitorTime(getOAuthDataForUserId(monitor.getUserId()));
		
		logger.info("Current energy monitor time for " + monitor + " is " + currentTedDate);
		
		Date offDate = currentTedDate;
		Calendar onCalendar = new GregorianCalendar();
		onCalendar.setTime(offDate);
		onCalendar.add(Calendar.MILLISECOND, -1 * (int)(labelOffTime.getTime() - labelOnTime.getTime()));
		
		// delegate user label detection to detection manager
		logger.info("Monitor search window: " + onCalendar.getTime() + " to " + offDate);
		LabelResult result = detectionManager.detectAcceptableUserTraining(monitor, onCalendar.getTime(), offDate);
		
		if(result == LabelResult.OK || force) {
			List<ApplianceStateTransition> transitions = new ArrayList<ApplianceStateTransition>();
			
			// 0 for the detection algorithm because it is user labeled
			UserAppliance userAppliance = database.getUserApplianceById(userApplianceId);
		
			ApplianceStateTransition onTransition = new GenericStateTransition(-1, userAppliance, 0, true, onCalendar.getTime().getTime());
			
			ApplianceStateTransition offTransition = new GenericStateTransition(-1, userAppliance, 0, false, offDate.getTime());
			transitions.add(onTransition);
			transitions.add(offTransition);
			database.storeUserOnOffLabels(transitions);
			return LabelResult.OK;
		} else {
			return result;
		}
	}
	
	public OAuthData getOAuthDataForUserId(String userId) {
		OAuthSecurityContextImpl oAuthContext = new OAuthSecurityContextImpl();
		oAuthContext.setAccessTokens(getOAuthTokensForUserId(userId));
		
		OAuthData oAuthData = new OAuthData(oAuthRequestProcessor, oAuthContext);
		return oAuthData;
	}

	private List<Ted5000> removeUnfetchableTeds(AggregatedTeds aggteds) {
		List<Ted5000> teds = aggteds.getTed5000();
		Iterator<Ted5000> iter = teds.iterator();
		while(iter.hasNext()) {
			Ted5000 ted = iter.next();
			if(ted.getFetch() == null || ted.getFetch().getFetch() == null || !ted.getFetch().getFetch().equals("true")) {
				iter.remove();
			}
		}
		
		return teds;
	}

	private final class StepgreenUserDetailsResponseHandler extends ResponseHandler<StepgreenUser, StepgreenUserDetails> {
		public StepgreenUserDetails extractValue(StepgreenUser userInfo) {
			StepgreenUserDetails info = new StepgreenUserDetails();
			info.setEmail(userInfo.getEmail());
			info.setUserId(userInfo.getId());
			return info;
		}
	}

	@Override
	@Transactional
	public void createUserGeneratedLabelsSurroundingAnonymousTransition(int transitionId, int userApplianceId) {
		// get transition
		GenericStateTransition transition = database.getAnonymousApplianceStateTransitionById(transitionId);
		
		// generate on and off labels 2 seconds before and after transition
		Calendar before = new GregorianCalendar();
		before.setTimeInMillis(transition.getTime());
		before.add(Calendar.SECOND, -2);
		
		Calendar after = new GregorianCalendar();
		after.setTimeInMillis(transition.getTime());
		after.add(Calendar.SECOND, 2);
		
		// generate the labels and save them
		UserAppliance userAppliance = database.getUserApplianceById(userApplianceId);
		
		List<ApplianceStateTransition> transitions = new ArrayList<ApplianceStateTransition>();
		
		ApplianceStateTransition onTransition = new GenericStateTransition(-1, userAppliance, 0, true, before.getTime().getTime());
		
		ApplianceStateTransition offTransition = new GenericStateTransition(-1, userAppliance, 0, false, after.getTime().getTime());
		transitions.add(onTransition);
		transitions.add(offTransition);
		database.storeUserOnOffLabels(transitions);
		
		// delete the anonymous transition - it will be labeled as a true appliance transition in the next retraining pass
		database.removeTransition(transition);
	}
}
