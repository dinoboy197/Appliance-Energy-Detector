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
import info.raack.appliancelabeler.data.Database;
import info.raack.appliancelabeler.model.energymonitor.Ted5000Monitor;
import info.raack.appliancelabeler.service.DataService;
import info.raack.appliancelabeler.service.OAuthRequestProcessor;
import info.raack.appliancelabeler.util.OAuthUnauthorizedException;
import info.raack.appliancelabeler.util.ResponseHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth.consumer.OAuthSecurityContextImpl;
import org.springframework.security.oauth.consumer.token.OAuthConsumerToken;
import org.springframework.stereotype.Component;

import edu.cmu.hcii.stepgreen.data.teds.AggregatedTeds;
import edu.cmu.hcii.stepgreen.data.teds.Mtu;
import edu.cmu.hcii.stepgreen.data.teds.Ted5000;

@Component
public class StepgreenTED5000ConfigurationLoader implements ConfigurationLoader<Ted5000Monitor> {
	private Logger logger = LoggerFactory.getLogger(StepgreenTED5000ConfigurationLoader.class);
	
	@Autowired
	private DataService dataService;
	
	@Autowired
	private OAuthRequestProcessor oAuthRequestProcessor;
	
	@Autowired
	private EmailSender emailSender;
	
	@Autowired
	private Database database;
	
	@Value("${stepgreen.basehost}")
	private String stepgreenBasehost;
	
	@Value("${energylabeler.url}")
	private String energylabelerUrl;

	private String tedDataPath = "http://%s/api/v1/users/%s/ted5000s.xml";
	
	
	public Collection<Ted5000Monitor> getActiveEnergyMonitors() {
		List<Ted5000Monitor> monitors = new ArrayList<Ted5000Monitor>();
		
		try {
			// get list of aggregated teds from which to collect data
			logger.debug("Collecting TED data from Stepgreen service...");
			Map<String,List<Ted5000>> userTeds = reloadConfigurations();
			
			// for each ted
			for(String userId : userTeds.keySet()) {
				for(Ted5000 ted : userTeds.get(userId)) {
					// check to see if fetch is turned on for this mtu
					if(ted.getFetch() != null) {
						for(Mtu mtu : ted.getTed5000Mtu()) {
							try {
								Ted5000Monitor monitor = new Ted5000Monitor(-1, userId, ted.getId(), mtu.getId(), stepgreenBasehost);
								logger.debug("Found new energy monitor for " + userId + "; " + monitor); 
								monitors.add(monitor);
							} catch (Exception e) {
								logger.error("Could get data from feed for " + ted, e);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("Could not retrieve TED data from monitor", e);
		}
		
		return monitors;
	}
	
	private Map<String, List<Ted5000>> reloadConfigurations() {
		// get url for all configurations
		
		String response = "";
		try {
			logger.debug("Requesting user TEDs");
			synchronized(this) {
				
				Map<String, List<Ted5000>> allTeds = new HashMap<String, List<Ted5000>>();
				
				// construct oauth access tokens
				Map<String, Map<String,OAuthConsumerToken>> userContexts = database.getOAuthTokensForAllUsers();
				
				for(String userId : userContexts.keySet()) {
					logger.debug("Request TEDs for " + userId);
					Map<String, OAuthConsumerToken> tokenMap = userContexts.get(userId);
					
					if(tokenMap.isEmpty()) {
						// don't request anything with no tokens
						continue;
					}
					
					OAuthSecurityContextImpl oAuthContext = new OAuthSecurityContextImpl();
					oAuthContext.setAccessTokens(tokenMap);
					
					String requestURI = String.format(tedDataPath, stepgreenBasehost, userId);
					
					try {
						allTeds.put(userId, oAuthRequestProcessor.processRequest(requestURI, oAuthContext, new ResponseHandler<AggregatedTeds,List<Ted5000>>() {
							public List<Ted5000> extractValue(AggregatedTeds ted) {
								return ted.getTed5000();
							}
						}));
					} catch (OAuthUnauthorizedException e2) {
						String email = database.getUserEmailForUserId(userId);
						logger.warn("Could not get list of TEDS for user " + userId + " - request through oauth is unauthorized");
						emailSender.sendGeneralEmail(email, "Your Stepgreen energy consumption authorization is no longer valid", 
								"At some point in the past, you used the Stepgreen Appliance Energy Visualization tool to see detailed usage of your appliance energy use.\n\n" +
								"We have become unable to access your Stepgreen energy data. If you wish to continue using the tool, please visit " + energylabelerUrl + " now. " +
								"We will be unable to collect and provide any more energy predictions until you do so.\n\n" +
								"Thanks,\n" +
								"The Stepgreen Appliance Energy Team");
						
						// now invalidate the tokens
						tokenMap.clear();
						dataService.saveOAuthTokensForUserId(userId, email, tokenMap);
					}
				}
				
				return allTeds;
			}
		} catch (Exception e) {
			throw new RuntimeException("Could not get list of TEDs from StepGreen: received " + response, e);
		}
	}
}
