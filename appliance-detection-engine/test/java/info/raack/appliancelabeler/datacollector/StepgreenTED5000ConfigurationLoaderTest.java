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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import info.raack.appliancedetection.common.email.EmailSender;
import info.raack.appliancelabeler.data.Database;
import info.raack.appliancelabeler.service.DataService;
import info.raack.appliancelabeler.service.OAuthRequestProcessor;
import info.raack.appliancelabeler.util.OAuthUnauthorizedException;
import info.raack.appliancelabeler.util.ResponseHandler;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.junit.Before;
import org.junit.Test;
import org.springframework.security.oauth.consumer.OAuthSecurityContextImpl;
import org.springframework.security.oauth.consumer.token.OAuthConsumerToken;
import org.xml.sax.SAXException;

public class StepgreenTED5000ConfigurationLoaderTest {
	private StepgreenTED5000ConfigurationLoader loader;
	
	private DataService dataService;
	private Database database;
	private EmailSender emailSender;
	private OAuthRequestProcessor oauthRequestProcessor;
	
	@Before public void setup() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, JAXBException, SAXException {
		dataService = mock(DataService.class);
		database = mock(Database.class);
		oauthRequestProcessor = mock(OAuthRequestProcessor.class);
		emailSender = mock(EmailSender.class);
		
		loader = new StepgreenTED5000ConfigurationLoader();
		Class<?> c = loader.getClass();
	    Field f = c.getDeclaredField("dataService");
	    f.setAccessible(true);
	    f.set(loader, dataService);
	    
	    f = c.getDeclaredField("oAuthRequestProcessor");
	    f.setAccessible(true);
	    f.set(loader, oauthRequestProcessor);
	    
	    f = c.getDeclaredField("emailSender");
	    f.setAccessible(true);
	    f.set(loader, emailSender);
	    
	    f = c.getDeclaredField("energylabelerUrl");
	    f.setAccessible(true);
	    f.set(loader, "http://www.energylabelerurl.com");
	    
	    f = c.getDeclaredField("database");
	    f.setAccessible(true);
	    f.set(loader, database);
	}
	
	@Test public void doNotGetMonitorInformationWithoutCredentials() {
		// set up expectation for the oauth request processor to return an exception
		String userId = "testuserid";
		String userEmail = "useremail";
		
		Map<String,OAuthConsumerToken> tokens = new HashMap<String, OAuthConsumerToken>();
		
		Map<String,Map<String,OAuthConsumerToken>> oauthmap = new HashMap<String,Map<String,OAuthConsumerToken>>();
		oauthmap.put(userId, tokens);
		
		when(database.getOAuthTokensForAllUsers()).thenReturn(oauthmap);
		
		// do test - no exception should be thrown
		loader.getActiveEnergyMonitors();
		
		// verify that the oauth tokens were requested and no emails are sent
		
		verify(dataService, never()).saveOAuthTokensForUserId(anyString(), anyString(), any(Map.class));
		verify(emailSender, never()).sendGeneralEmail(anyString(), anyString(), anyString());
		verify(oauthRequestProcessor, never()).processRequest(anyString(), any(OAuthSecurityContextImpl.class), any(ResponseHandler.class));
	}
	
	@Test public void invalidateOAuthCredentialAndEmailUserOnOAuthUnauthorizedException() throws OAuthUnauthorizedException {
		// set up expectation for the oauth request processor to return an exception
		String userId = "testuserid";
		String userEmail = "useremail";
		
		Map<String,OAuthConsumerToken> tokens = new HashMap<String, OAuthConsumerToken>();
		tokens.put("hello", mock(OAuthConsumerToken.class));
		
		Map<String,Map<String,OAuthConsumerToken>> oauthmap = new HashMap<String,Map<String,OAuthConsumerToken>>();
		oauthmap.put(userId, tokens);
		
		when(database.getOAuthTokensForAllUsers()).thenReturn(oauthmap);
		when(database.getUserEmailForUserId(userId)).thenReturn(userEmail);
		
		doThrow(new OAuthUnauthorizedException()).when(oauthRequestProcessor).processRequest(anyString(), any(OAuthSecurityContextImpl.class), any(ResponseHandler.class));
		
		// do test - no exception should be thrown
		loader.getActiveEnergyMonitors();
		
		Map<String,OAuthConsumerToken> newoauthMap = new HashMap<String,OAuthConsumerToken>();
		
		verify(emailSender, times(1)).sendGeneralEmail(userEmail, "Your Stepgreen energy consumption authorization is no longer valid", "At some point in the past, you used the Stepgreen Appliance Energy Visualization tool to see detailed usage of your appliance energy use.\n\nWe have become unable to access your Stepgreen energy data. If you wish to continue using the tool, please visit http://www.energylabelerurl.com now. We will be unable to collect and provide any more energy predictions until you do so.\n\nThanks,\nThe Stepgreen Appliance Energy Team");
		verify(dataService, times(1)).saveOAuthTokensForUserId(userId, userEmail, newoauthMap);
		
	}
}
