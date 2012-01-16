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

import java.util.List;

import org.springframework.stereotype.Component;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

@Component
public class JTransformsFrequencyAnalyzer implements FrequencyAnalyzer {

	@Override
	public double[] retrieveMostPromimentWavelengthBoxAmplitudes(List<SecondData> measurements, int wavelengthBoxSize) {
		
		// calculate boxes
		int waveLengthWindow = measurements.size() / 2 - 0;
		int boxes = (int)Math.ceil((double)waveLengthWindow / (double)wavelengthBoxSize);
		
		double[] largestBoxedResponses = new double[boxes];
		
		// prepare data for FFT
		double[] data = new double[measurements.size()];
		
		int total = 0;
		for(SecondData measurement : measurements) {
			data[total++] = measurement.getPower();
		}
		
		// perform FFT
		DoubleFFT_1D fft = new DoubleFFT_1D(measurements.size());
		fft.realForward(data);
		
		
		// perform spectral analysis
		double[] spectrum = new double[measurements.size() / 2];
		for(int i = 0; i < measurements.size() / 2; i++) {
		    spectrum[i] = Math.sqrt( Math.pow(data[2*i],2) + Math.pow(data[2*i+1],2) );
		}
		
		// loop through frequencies and select the largest ones
		double frequencyPiece = (double)0.5 / (spectrum.length - 1);
		for(int i = 1; i < spectrum.length; i++) {
			// greatest frequency in each box goes into list
			double frequency = (double)i * frequencyPiece;
			double wavelength = (double)1.0 / frequency;
			
			double amplitude = spectrum[i];
			
			if(wavelength < 600 && amplitude / frequency > 500 * measurements.size()) {
				//System.out.println(wavelength + ": " + amplitude / frequency);
				// only select the wavelengths with high enough power for our choosing
				
				if(wavelength < (measurements.size() / 2)) {
					int wavelengthBox = (int)(wavelength / wavelengthBoxSize);
					largestBoxedResponses[wavelengthBox] = Math.max(amplitude / frequency, largestBoxedResponses[wavelengthBox]);
				}
			}
		}
		
		return largestBoxedResponses;
	}

}
