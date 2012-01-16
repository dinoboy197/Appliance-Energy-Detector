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
package info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.frequencyanalysis;

import static org.junit.Assert.assertNull;

import org.junit.Test;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

public class SWFRDetectorTest {
	
	@Test
	public void findBeginningAndEndTransitions() {
		// bad constructor! Shouldn't create a test dependency on another class, but until I can properly mock out that test, this will have to do
		
		SlidingWindowFrequencyResponseDetector detector = new SlidingWindowFrequencyResponseDetector(new int[] {15,30,60}, 5, new JTransformsFrequencyAnalyzer());

		int total = 0;
		
		// power curve is sin wave with two constant frequencies with a linearly varying amplitude multiplier
		// flat 0 power
		while(total++ < 120) {
			SecondData data = new SecondData();
			data.setPower(0);
			
			// verify that no response is detected
			assertNull("Detected frequency response where there should be none at beginning of test", detector.detectCurrentTransition(data));
		}
		
		
		// increasing in power
		while(total++ < 120) {
			int power = grabPowerAtTime(total, (double)total / (double)60);
			SecondData data = new SecondData();
			data.setPower(power);
			
			
		}
		
		// flat high power
		// a transition should be detected since the frequency power has stabilized
		while(total++ < 180) {
			int power = grabPowerAtTime(total, 1);
			SecondData data = new SecondData();
			data.setPower(power);
	
			FrequencyResponseMoment moment = detector.detectCurrentTransition(data);
			if(moment != null) {
				System.out.println("Detected transition " + moment + " at " + total);
			}
		}
		
		// decreasing in power
		while(total++ < 210) {
			int power = grabPowerAtTime(total, (double)(150 - total) / (double)30);
			SecondData data = new SecondData();
			data.setPower(power);
			
			//assertNull("Detected frequency response at " + total + " where there should be none at during decline", detector.detectCurrentTransition(data));

		}

		// TODO - somewhere in here, a transition should be detected to go back to off
		// flat 0 power
		while(total++ < 330) {
			SecondData data = new SecondData();
			data.setPower(0);
			
			FrequencyResponseMoment moment = detector.detectCurrentTransition(data);
			if(moment != null) {
				System.out.println("Detected transition " + moment + " at " + total);
			}
			//assertNull("Detected frequency response at " + total + " where there should be none at end of test", detector.detectCurrentTransition(data));
		}
		
	}

	private int grabPowerAtTime(int step, double d) {
		int power = (int)((100.0f * Math.sin(2.0f * Math.PI * (float)step / 8.0f) + 75.0f * Math.sin(2.0f * Math.PI * (float)step / 27.0f)) * d) + 200;
		//System.out.println(power);
		return power;
	}
}
