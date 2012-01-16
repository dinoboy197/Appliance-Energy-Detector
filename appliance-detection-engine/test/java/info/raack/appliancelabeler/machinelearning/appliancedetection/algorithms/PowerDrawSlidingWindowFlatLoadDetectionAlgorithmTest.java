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
package info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import info.raack.appliancelabeler.data.batch.ItemReader;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.util.ItemReaderTestUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

public class PowerDrawSlidingWindowFlatLoadDetectionAlgorithmTest {

	private PowerDrawSlidingWindowFlatLoadDetectionAlgorithm algo;
	private UserAppliance userAppliance;
	
	@Before public void setup() {
		algo = new PowerDrawSlidingWindowFlatLoadDetectionAlgorithm();
		userAppliance = mock(UserAppliance.class);
	}
	
	
	private ItemReader<SecondData> createSecondDataReader(int[] powerMeasurements) {
		return ItemReaderTestUtils.createSecondDataReader(new GregorianCalendar(), powerMeasurements);
	}
	
	@Test public void noDetectionForNoInstability() {
		ItemReader<SecondData> dataReader = createSecondDataReader(new int[] {5,6,4,6,3,2,5,3,2,5,4,2,3,4,4,4,4,3,6,3,5,4,3});
		
		assertEquals("Transitions detected but not expected", 0, algo.detectStateTransitionsInternal((AlgorithmResult)null, userAppliance, dataReader, 1).keySet().size());
	}
	
	@Test public void noDetectionForBeginningInstabilityButNoTransition() {
		ItemReader<SecondData> dataReader = createSecondDataReader(new int[] {5,6,4,6,3,2,5,3,2,5,4,2,3,4,4,4,4,5,7,9,13,25});
		
		assertEquals("Transitions detected but not expected", 0, algo.detectStateTransitionsInternal((AlgorithmResult)null, userAppliance, dataReader, 1).keySet().size());
	}
	
	@Test public void noDetectionForBeginningInstabilityNoTransitionStabilityTransition() {
		ItemReader<SecondData> dataReader = createSecondDataReader(new int[] {5,6,4,6,3,2,5,3,2,5,4,2,3,4,4,4,4,5,7,9,13,25,37,40,43,42,40,42});
		
		assertEquals("Transitions detected but not expected", 0, algo.detectStateTransitionsInternal((AlgorithmResult)null, userAppliance, dataReader, 1).keySet().size());
	}
	
	@Test public void noDetectionForBeginningInstabilityTransitionButNoEndStability() {
		ItemReader<SecondData> dataReader = createSecondDataReader(new int[] {5,6,4,6,3,2,5,3,2,5,4,2,3,4,4,4,4,5,7,9,13,25,40,65,80,120,131});
		
		assertEquals("Transitions detected but not expected", 0, algo.detectStateTransitionsInternal((AlgorithmResult)null, userAppliance, dataReader, 1).keySet().size());
	}
	
	@Test public void detectionOfBasicSwitchLoad() {
		Calendar cal = new GregorianCalendar();
		cal.add(Calendar.MILLISECOND, -1 * cal.get(Calendar.MILLISECOND));
		
		ItemReader<SecondData> dataReader = ItemReaderTestUtils.createSecondDataReader(cal, new int[] {5,6,4,6,3,70,72,69,73,74,15,17,13,15,14});
		
		Map<Long, List<ApplianceStateTransition>> transitions = algo.detectStateTransitionsInternal((AlgorithmResult)null, userAppliance, dataReader, 1);
		assertEquals("Transitions detected but not expected", 2, transitions.keySet().size());
		
		Iterator<Long> iterator = transitions.keySet().iterator();
		
		// first transition
		List<ApplianceStateTransition> list = transitions.get(iterator.next());
		assertEquals("Unexpected number of power transitions", 1, list.size());
		assertEquals("Unexpected power delta", 67, (int)list.get(0).getVariableValues()[0]);
		
		cal.add(Calendar.SECOND, 5);
		assertEquals("Unexpected transition time", cal.getTime(), new Date(list.get(0).getTime()));
		
		// next transition
		list = transitions.get(iterator.next());
		assertEquals("Unexpected number of power transitions", 1, list.size());
		assertEquals("Unexpected power delta", -56, (int)list.get(0).getVariableValues()[0]);
		
		cal.add(Calendar.SECOND, 5);
		assertEquals("Unexpected transition time", cal.getTime(), new Date(list.get(0).getTime()));
	}
	
	@Test public void detectionOfAngularLoad() {
		Calendar cal = new GregorianCalendar();
		cal.add(Calendar.MILLISECOND, -1 * cal.get(Calendar.MILLISECOND));
		
		ItemReader<SecondData> dataReader = ItemReaderTestUtils.createSecondDataReader(cal, new int[] {5,6,4,6,3,6,10,15,20,35,46,52,59,65,68,70,72,69,73,74,72,70,65,57,50,46,42,35,25,23,18,15,17,13,15,14});
		
		Map<Long, List<ApplianceStateTransition>> transitions = algo.detectStateTransitionsInternal((AlgorithmResult)null, userAppliance, dataReader, 1);
		assertEquals("Transitions detected but not expected", 2, transitions.keySet().size());
		
		Iterator<Long> iterator = transitions.keySet().iterator();
		
		// first transition
		List<ApplianceStateTransition> list = transitions.get(iterator.next());
		assertEquals("Unexpected number of power transitions", 1, list.size());
		assertEquals("Unexpected power delta", 62, (int)list.get(0).getVariableValues()[0]);
		
		cal.add(Calendar.SECOND, 7);
		assertEquals("Unexpected transition time", cal.getTime(), new Date(list.get(0).getTime()));
		
		// next transition
		list = transitions.get(iterator.next());
		assertEquals("Unexpected number of power transitions", 1, list.size());
		assertEquals("Unexpected power delta", -54, (int)list.get(0).getVariableValues()[0]);
		
		cal.add(Calendar.SECOND, 16);
		assertEquals("Unexpected transition time", cal.getTime(), new Date(list.get(0).getTime()));
	}
}
