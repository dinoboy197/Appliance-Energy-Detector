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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import info.raack.appliancelabeler.data.Database;
import info.raack.appliancelabeler.machinelearning.ApplianceDetectionManager;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.GenericStateTransition;
import info.raack.appliancelabeler.model.energymonitor.EnergyMonitor;
import info.raack.appliancelabeler.security.OAuthData;
import info.raack.appliancelabeler.service.DataService.LabelResult;
import info.raack.appliancelabeler.util.ResponseHandler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.security.oauth.consumer.token.OAuthConsumerToken;
import org.xml.sax.SAXException;

import edu.cmu.hcii.stepgreen.data.teds.AggregatedTeds;
import edu.cmu.hcii.stepgreen.data.teds.FetchType;
import edu.cmu.hcii.stepgreen.data.teds.Ted5000;

public class DefaultDataServiceTest {
	private DefaultDataService dataService;
	
	private Database database;
	private OAuthRequestProcessor oauthRequestProcessor;
	private EnergyMonitor energyMonitor;
	private ApplianceDetectionManager detectionManager;
	private String stepgreenBaseHost;
	
	@Before public void setup() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, JAXBException, SAXException {
		database = mock(Database.class);
		
		energyMonitor = mock(EnergyMonitor.class);
		oauthRequestProcessor = mock(OAuthRequestProcessor.class);
		detectionManager = mock(ApplianceDetectionManager.class);
	    stepgreenBaseHost = "thebasehost";
		
		when(energyMonitor.getUserId()).thenReturn("theuserid");
		
		dataService = new DefaultDataService();
		Class<?> c = dataService.getClass();
	    Field f = c.getDeclaredField("database");
	    f.setAccessible(true);
	    f.set(dataService, database);
	    
	    f = c.getDeclaredField("oAuthRequestProcessor");
	    f.setAccessible(true);
	    f.set(dataService, oauthRequestProcessor);
	    
	    f = c.getDeclaredField("stepgreenBasehost");
	    f.setAccessible(true);
	    f.set(dataService, stepgreenBaseHost);
	    
	    f = c.getDeclaredField("detectionManager");
	    f.setAccessible(true);
	    f.set(dataService, detectionManager);
	    
	    dataService.init();
	}
	
	


	class IsMatchingOAuthData extends ArgumentMatcher<OAuthData> {
		private Map<String, OAuthConsumerToken> map;
		
		public IsMatchingOAuthData(Map<String, OAuthConsumerToken> map) {
			this.map = map;
		}

		@Override
		public boolean matches(Object argument) {
			return argument instanceof OAuthData && ((OAuthData)argument).getRequestProcessor().equals(oauthRequestProcessor) && ((OAuthData)argument).getSecurityContext().getAccessTokens().equals(map);
		}
	}
	
	@Test public void goodUserLabelsAreAccepted() {
		// off label is now, on label was 30 seconds ago
		Calendar userOffLabelCal = new GregorianCalendar();
		Calendar userOnLabelCal = (Calendar)userOffLabelCal.clone();
		userOnLabelCal.add(Calendar.SECOND, -30);
		
		// ted time is 2 minutes ahead of actual time
		Calendar tedEndCal = (Calendar)userOffLabelCal.clone();
		tedEndCal.add(Calendar.SECOND, 120);
		
		Calendar tedStartCal = (Calendar)tedEndCal.clone();
		tedStartCal.add(Calendar.SECOND, -30);
		
		Map<String,OAuthConsumerToken> map = mock(Map.class);
		
		when(database.getOAuthTokensForUserId("theuserid")).thenReturn(map);
		
		when(energyMonitor.getCurrentMonitorTime(argThat(new IsMatchingOAuthData(map)))).thenReturn(tedEndCal.getTime());
		
		UserAppliance userAppliance = mock(UserAppliance.class);
		when(database.getUserApplianceById(1)).thenReturn(userAppliance);
		
		when(detectionManager.detectAcceptableUserTraining(energyMonitor, tedStartCal.getTime(), tedEndCal.getTime())).thenReturn(LabelResult.OK);
		
		// run test
		LabelResult result = dataService.createUserGeneratedLabelsForUserApplianceId(energyMonitor, 1, userOnLabelCal.getTime(), userOffLabelCal.getTime(), false);
		assertEquals(LabelResult.OK, result);
		
		Calendar onLabelCal = (Calendar)userOnLabelCal.clone();
		// go five seconds into start date
		onLabelCal.add(Calendar.SECOND, 120);
		
		Calendar offLabelCal = (Calendar)userOffLabelCal.clone();
		offLabelCal.add(Calendar.SECOND, 120);
		
		List<ApplianceStateTransition> transitions = new ArrayList<ApplianceStateTransition>();
		GenericStateTransition on = new GenericStateTransition(-1, userAppliance, 0, true, onLabelCal.getTimeInMillis());
		GenericStateTransition off = new GenericStateTransition(-1, userAppliance, 0, false, offLabelCal.getTimeInMillis());
		transitions.add(on);
		transitions.add(off);
		
		// verify that labels were added
		verify(database, times(1)).storeUserOnOffLabels(transitions);
	}
	
	@Test public void userLabelsWithoutDownSpikeAreNotAccepted() {
		// off label is now, on label was 30 seconds ago
		Calendar userOffLabelCal = new GregorianCalendar();
		Calendar userOnLabelCal = (Calendar)userOffLabelCal.clone();
		userOnLabelCal.add(Calendar.SECOND, -30);
		
		// ted time is 2 minutes ahead of actual time
		Calendar tedEndCal = (Calendar)userOffLabelCal.clone();
		tedEndCal.add(Calendar.SECOND, 120);
		
		Calendar tedStartCal = (Calendar)tedEndCal.clone();
		tedStartCal.add(Calendar.SECOND, -30);
		
		Map<String,OAuthConsumerToken> map = mock(Map.class);
		
		when(database.getOAuthTokensForUserId("theuserid")).thenReturn(map);
		
		when(energyMonitor.getCurrentMonitorTime(argThat(new IsMatchingOAuthData(map)))).thenReturn(tedEndCal.getTime());
		
		UserAppliance userAppliance = mock(UserAppliance.class);
		when(database.getUserApplianceById(1)).thenReturn(userAppliance);
		
		when(detectionManager.detectAcceptableUserTraining(energyMonitor, tedStartCal.getTime(), tedEndCal.getTime())).thenReturn(LabelResult.NO_POWER_DECREASE);
		
		// run test
		LabelResult result = dataService.createUserGeneratedLabelsForUserApplianceId(energyMonitor, 1, userOnLabelCal.getTime(), userOffLabelCal.getTime(), false);
		assertEquals(LabelResult.NO_POWER_DECREASE, result);
	}
	
	@Test public void userLabelsWithoutUpSpikeAreNotAccepted() {
		// off label is now, on label was 30 seconds ago
		Calendar userOffLabelCal = new GregorianCalendar();
		Calendar userOnLabelCal = (Calendar)userOffLabelCal.clone();
		userOnLabelCal.add(Calendar.SECOND, -30);
		
		// ted time is 2 minutes ahead of actual time
		Calendar tedEndCal = (Calendar)userOffLabelCal.clone();
		tedEndCal.add(Calendar.SECOND, 120);
		
		Calendar tedStartCal = (Calendar)tedEndCal.clone();
		tedStartCal.add(Calendar.SECOND, -30);
		
		Map<String,OAuthConsumerToken> map = mock(Map.class);
		
		when(database.getOAuthTokensForUserId("theuserid")).thenReturn(map);
		
		when(energyMonitor.getCurrentMonitorTime(argThat(new IsMatchingOAuthData(map)))).thenReturn(tedEndCal.getTime());
		
		UserAppliance userAppliance = mock(UserAppliance.class);
		when(database.getUserApplianceById(1)).thenReturn(userAppliance);
		
		when(detectionManager.detectAcceptableUserTraining(energyMonitor, tedStartCal.getTime(), tedEndCal.getTime())).thenReturn(LabelResult.NO_POWER_INCREASE);
		
		// run test
		LabelResult result = dataService.createUserGeneratedLabelsForUserApplianceId(energyMonitor, 1, userOnLabelCal.getTime(), userOffLabelCal.getTime(), false);
		assertEquals(LabelResult.NO_POWER_INCREASE, result);
	}
	
	@Test public void userLabelsWithoutGoingDownToNormalPowerAreNotAccepted() {
		// off label is now, on label was 30 seconds ago
		Calendar userOffLabelCal = new GregorianCalendar();
		Calendar userOnLabelCal = (Calendar)userOffLabelCal.clone();
		userOnLabelCal.add(Calendar.SECOND, -30);
		
		// ted time is 2 minutes ahead of actual time
		Calendar tedEndCal = (Calendar)userOffLabelCal.clone();
		tedEndCal.add(Calendar.SECOND, 120);
		
		Calendar tedStartCal = (Calendar)tedEndCal.clone();
		tedStartCal.add(Calendar.SECOND, -30);
		
		Map<String,OAuthConsumerToken> map = mock(Map.class);
		
		when(database.getOAuthTokensForUserId("theuserid")).thenReturn(map);
		
		when(energyMonitor.getCurrentMonitorTime(argThat(new IsMatchingOAuthData(map)))).thenReturn(tedEndCal.getTime());
		
		UserAppliance userAppliance = mock(UserAppliance.class);
		when(database.getUserApplianceById(1)).thenReturn(userAppliance);
		
		when(detectionManager.detectAcceptableUserTraining(energyMonitor, tedStartCal.getTime(), tedEndCal.getTime())).thenReturn(LabelResult.NOT_TURNED_OFF);
		
		// run test
		LabelResult result = dataService.createUserGeneratedLabelsForUserApplianceId(energyMonitor, 1, userOnLabelCal.getTime(), userOffLabelCal.getTime(), false);
		assertEquals(LabelResult.NOT_TURNED_OFF, result);
	}
	
	@Test public void doNotSendDataAlreadySent() {
		/*final SecondData one = new SecondData();
	    one.setYear(1); one.setMonth(2); one.setDay(3); one.setHour(4); one.setMinute(5); one.setSecond(6);
	    final SecondData two = new SecondData();
	    two.setYear(1); two.setMonth(2); two.setDay(3); two.setHour(4); two.setMinute(5); two.setSecond(6);
	    
	    List<SecondData> newData = new ArrayList<SecondData>() {{ add(one); add(two); }};
		
		//List<SecondData> data = mock(List.class);
		List<ApplianceStateTransition> detectedStateTransitions = mock(List.class);
	    
	    dataService.storeDataAndStateTransitions(energyMonitor, newData, 50L, detectedStateTransitions);
	    
	    verify(database, times(1)).storeData(energyMonitor, newData, 50L);
	    verify(database, times(1)).storeStateTransitions(energyMonitor, detectedStateTransitions);*/
	}
	
	class IsValidTransitionList extends ArgumentMatcher<List<ApplianceStateTransition>> {

		private long time1;
		private long time2;
		private UserAppliance userAppliance;
		
		public IsValidTransitionList(Calendar cal1, Calendar cal2, UserAppliance userAppliance) {
			time1 = cal1.getTimeInMillis();
			time2 = cal2.getTimeInMillis();
			this.userAppliance = userAppliance;
		}
		
		@Override
		public boolean matches(Object argument) {
			if(argument instanceof List<?>) {
				List<ApplianceStateTransition> list = (List<ApplianceStateTransition>)argument;
				boolean match = list.get(0).getTime() == time1 && list.get(0).isOn() && list.get(0).getDetectionAlgorithmId() == 0 && list.get(0).getUserAppliance().equals(userAppliance) &&
						list.get(1).getTime() == time2 && list.get(1).isOn() == false && list.get(1).getDetectionAlgorithmId() == 0 && list.get(1).getUserAppliance().equals(userAppliance);
				
				return match;
			}
			return false;
		}
	}
	
	@Test public void createSurroundingTransitionsForAnonymousTransitionCorrectly() {
		GenericStateTransition transition = mock(GenericStateTransition.class);
		UserAppliance userAppliance = mock(UserAppliance.class);
		
		Calendar cal = new GregorianCalendar();
		Calendar cal2 = (Calendar)cal.clone();
		Calendar cal3 = (Calendar)cal.clone();
		cal2.add(Calendar.SECOND, -2);
		cal3.add(Calendar.SECOND, 2);
		
		when(transition.getTime()).thenReturn(cal.getTime().getTime());
		
		int transitionId = 15;
		int userApplianceId = 16;
		
		when(database.getAnonymousApplianceStateTransitionById(transitionId)).thenReturn(transition);
		when(database.getUserApplianceById(userApplianceId)).thenReturn(userAppliance);
		
		
		dataService.createUserGeneratedLabelsSurroundingAnonymousTransition(transitionId, userApplianceId);
		
		
		verify(database, times(1)).storeUserOnOffLabels(argThat(new IsValidTransitionList(cal2, cal3, userAppliance)));
		verify(database, times(1)).removeTransition(transition);
	}
	
	private final class AnonymousResponseHandler implements Answer<List<Ted5000>> {
		private AggregatedTeds teds;
		
		public AnonymousResponseHandler(AggregatedTeds teds) {
			this.teds = teds;
		}

		@Override
		public List<Ted5000> answer(InvocationOnMock invocation) throws Throwable {
			ResponseHandler<AggregatedTeds, List<Ted5000>> responseHandler = (ResponseHandler<AggregatedTeds, List<Ted5000>>) invocation.getArguments()[1];
			
			return responseHandler.extractValue(teds);
		}
	}
	
	@Test public void gettingTedsWorksWithExistingContext() {
		
		String userId = "theuserid";
		
		// only last ted has fetch = true
		Ted5000 ted1 = mock(Ted5000.class);
		
		Ted5000 ted2 = mock(Ted5000.class);
		FetchType fetch = mock(FetchType.class);
		when(ted2.getFetch()).thenReturn(fetch);
		
		Ted5000 ted3 = mock(Ted5000.class);
		FetchType fetch2 = mock(FetchType.class);
		when(fetch2.getFetch()).thenReturn("true");
		
		when(ted3.getFetch()).thenReturn(fetch2);
		
		List<Ted5000> tedList = new ArrayList<Ted5000>(Arrays.asList(new Ted5000[] {ted1,ted2,ted3}));
		List<Ted5000> newList = new ArrayList<Ted5000>(Arrays.asList(new Ted5000[] {ted3}));
		AggregatedTeds teds = mock(AggregatedTeds.class);
		when(teds.getTed5000()).thenReturn(tedList);
		
		when(oauthRequestProcessor.processRequest(eq("http://thebasehost/api/v1/users/theuserid/ted5000s.xml"), any(ResponseHandler.class))).thenAnswer(new AnonymousResponseHandler(teds));
		
		assertEquals(newList, dataService.getTEDIdsForUserId(userId, false));
	}
	
	
	
	
}
