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

import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.IterativeSecondDataProcessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

/**
 * This class is NOT thread-safe.
 * 
 * @author traack
 *
 */
public class SlidingWindowFrequencyResponseDetector extends IterativeSecondDataProcessor<FrequencyResponseMoment> {

	private FrequencyAnalyzer frequencyAnalyzer;
	
	private int[] windowSizes;
	private int wavelengthBoxSize;
	private int stableTime = 10;
	private long differenceThreshold = 40000;
	private long stabilityThreshold = 20000;
	
	private boolean previouslyStable = true;
	private double[] lastStableResponses = null;
	private long lastStablePower = 0;
	private long lastStableStartTime = 0;
	
	private LinkedList<double[]> lastMaxResponses = new LinkedList<double[]>();
	
	public SlidingWindowFrequencyResponseDetector(int[] windowSizes, int wavelengthBoxSize, FrequencyAnalyzer frequencyAnalyzer) {
		super(windowSizes[windowSizes.length - 1]);
		
		this.windowSizes = windowSizes;
		this.wavelengthBoxSize = wavelengthBoxSize;
		this.frequencyAnalyzer = frequencyAnalyzer;
		
		lastMaxResponses.add(null);
	}

	/**
	 * Power spectrum response detector which finds all significant wavelength responses in a variety of power spectrum box sizes,
	 * returning a frequency response vector if any of the responses are positive.
	 */
	@Override
	protected FrequencyResponseMoment detectCurrentTransitionInternal(List<SecondData> lastMeasurements) {
		
		FrequencyResponseMoment moment = null;
		
		double[] maxResponses = new double[windowSizes[windowSizes.length - 1] / wavelengthBoxSize];
		                                      
		// loop through all window sizes performing power spectrum analysis
		for(int windowSize : windowSizes) {
			double[] responses = frequencyAnalyzer.retrieveMostPromimentWavelengthBoxAmplitudes(lastMeasurements.subList(0, windowSize), wavelengthBoxSize);
			
			for(int i = 0; i < responses.length; i++) {
				maxResponses[i] = Math.max(maxResponses[i], responses[i]);
			}
		}
		
		lastMaxResponses.add(maxResponses);
		
		if(lastMaxResponses.size() > stableTime) {
				
			// remove the first added maxResponse
			lastMaxResponses.pop();
			
			// check for stability
			boolean stable = isStable(lastMaxResponses, stabilityThreshold);
			
			// if previously stable and we are now going unstable, we may be undergoing a transition
			if(previouslyStable && !stable) {
				// mark this power level and time
				previouslyStable = false;
			} else if(stable){

				long newPower = getPower(maxResponses);
				
				if(!previouslyStable) {
				// else if previously unstable and we are going stable, we may be at the end of a transition
					previouslyStable = true;
					
					// grab this power level and time
					long diff = newPower - lastStablePower;
					
					// if this power level is at least the threshold away from the previous saved unstability point
					long responseTime = lastStableStartTime + stableTime * 1000;
					if(Math.abs(diff) >= differenceThreshold) {
						boolean on = diff > 0;
						
						// need to track the same positive frequency responses from the "on" detection with the "off" detection
						moment = new FrequencyResponseMoment(responseTime, on ? maxResponses : lastStableResponses, diff > 0);
					}
					
					lastStableResponses = maxResponses;
				}
	
				if(newPower == 0) {
					int k = 47;
				}
				lastStablePower = getPower(maxResponses);
				
				lastStableStartTime = lastMeasurements.get(0).getCalLong();
			}
		}
		
		return moment;
	}

	// max change in any of the maxresponse buckets should be less than differenceThreshold
	private boolean isStable(LinkedList<double[]> lastMaxResponses, long stabilityThreshold) {
		if(lastMaxResponses.size() == 0) {
			return false;
		}
		
		double[] powers = new double[lastMaxResponses.size()];
		
		for(int i = 0; i < lastMaxResponses.get(0).length; i++) {
			// work on i'th wavelength box
			
			for(int j = 0; j < lastMaxResponses.size(); j++) {
				// add i'th wavelength box from j'th maxResponse vector
				powers[j] = lastMaxResponses.get(j)[i];
			}
			
			// is the max difference greater than the threshold?
			Arrays.sort(powers);
			if(powers[powers.length - 1] - powers[0] > stabilityThreshold) {
				return false;
			}
		}
		
		// haven't broken stability threshold yet
		return true;
	}

	private long getPower(double[] maxResponses) {
		long total = 0;
		for(double response : maxResponses) {
			total += response;
		}
		return total;
	}
}
