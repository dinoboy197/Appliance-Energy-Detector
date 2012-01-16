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

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

public class JTransformsFrequencyAnalyzerTest {

	private JTransformsFrequencyAnalyzer analyzer;
	
	@Before
	public void setup() {
		analyzer = new JTransformsFrequencyAnalyzer();
	}
	
	@Test
	public void mainFrequenciesIdentified() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
		/*List<SecondData> data = new ArrayList<SecondData>();
		
		double amp1 = 150;
		double amp2 = 270;
		double amp3 = 75;
		
		double freq1 = 0.076923077; // wavelength of 13 seconds
		double freq2 = 0.333; // wavelength of 3 seconds
		double freq3 = 0.037037037; // wavelength of 27 seconds
		
		for(int i = 0; i < 60; i++) {
			double power = 
				amp1 * Math.sin((double)2*Math.PI * (double)i * freq1) +
				amp2 * Math.sin((double)2*Math.PI * (double)i * freq2 + 7) + 
				amp3 * Math.sin((double)2*Math.PI * (double)i * freq3 + 25) + 800;
			
			SecondData second = new SecondData();
			Class<?> c = second.getClass().getSuperclass();
		    Field f = c.getDeclaredField("power");
		    f.setAccessible(true);
		    f.set(second, (int)power);
		    
			data.add(second);
		}
		
		double[] results = analyzer.retrieveMostPromimentWavelengthBoxAmplitudes(data, 5);
		
		assertEquals(12, results.length);
		assertEquals(0, results[0], 10);
		assertEquals(0, results[1], 10);
		assertEquals(40000, results[2], 10000);
		assertEquals(0, results[3], 10);
		assertEquals(0, results[4], 0);
		assertEquals(60000, results[5], 10000);
		assertEquals(0, results[6], 10);
		assertEquals(0, results[7], 10);
		assertEquals(0, results[8], 10);
		assertEquals(0, results[9], 10);
		assertEquals(0, results[10], 10);
		assertEquals(0, results[11], 10);*/
		
	}
}
