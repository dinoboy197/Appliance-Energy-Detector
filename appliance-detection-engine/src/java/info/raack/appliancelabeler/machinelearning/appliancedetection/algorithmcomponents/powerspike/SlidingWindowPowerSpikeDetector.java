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
package info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.powerspike;

import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithmcomponents.IterativeSecondDataProcessor;

import java.util.ArrayList;
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
public class SlidingWindowPowerSpikeDetector extends IterativeSecondDataProcessor<PowerSpike> {

	private int stableTime;
	private int stabilityThreshold;
	private int differenceThreshold;
	private boolean previouslyStable = true;
	private int lastStablePower = 0;
	private long lastStableStartTime = 0;
	private long lastStableEndTime = 0;
	
	public SlidingWindowPowerSpikeDetector(int stableTime, int stabilityThreshold, int differenceThreshold)
	{
		super(stableTime);
		this.stableTime = stableTime;
		this.stabilityThreshold = stabilityThreshold;
		this.differenceThreshold = differenceThreshold;
	}
	
	protected PowerSpike detectCurrentTransitionInternal(List<SecondData> lastMeasurements) {
		PowerSpike powerSpike = null;
		
		// check for stability
		boolean stablePower = isStable(lastMeasurements, stabilityThreshold);
		
		// if previously stable and we are now going unstable, we may be undergoing a transition
		if(previouslyStable && !stablePower) {
			// mark this power level and time
			previouslyStable = false;
		} else if(stablePower){
			if(!previouslyStable) {
			// else if previously unstable and we are going stable, we may be at the end of a transition
				int newPower = getMidPower(lastMeasurements);
				long newStableStartTime = lastMeasurements.get(0).getCalLong();
				
				int transitionDuration = (int)(newStableStartTime - lastStableEndTime) / 1000;
				
				if(transitionDuration > 5) {
					int sd = 7;
				}
				previouslyStable = true;
				
				// grab this power level and time
				int diff = newPower - lastStablePower;
				
				// if this power level is at least the threshold away from the previous saved unstability point
				long spikeTime = lastStableStartTime + stableTime * 1000;
				if(Math.abs(diff) >= differenceThreshold) {
					powerSpike = new PowerSpike(spikeTime, diff, transitionDuration);
				}
			}

			lastStablePower = getMidPower(lastMeasurements);
			lastStableStartTime = lastMeasurements.get(0).getCalLong();
			lastStableEndTime = lastMeasurements.get(lastMeasurements.size() - 1 ).getCalLong();
		}
		
		
		return powerSpike;
	}
	
	private int getMidPower(List<SecondData> lastMeasurements) {
		int result = operateOnFirstAndLast(lastMeasurements, true);
		return result  / 2;
	}

	private boolean isStable(List<SecondData> lastMeasurements, int stabilityThreshold) {
		int result = operateOnFirstAndLast(lastMeasurements, false);
		return result < stabilityThreshold;
	}

	private int operateOnFirstAndLast(List<SecondData> lastMeasurements, boolean sum) {
		List<Integer> powers = new ArrayList<Integer>();
		if(lastMeasurements.size() == 0) {
			return 0;
		}
		for(SecondData data : lastMeasurements) {
			powers.add(data.getPower());
		}
		Collections.sort(powers);
		int result = powers.get(powers.size() - 1) + (sum ? powers.get(0) : -1 * powers.get(0));
		return result;
	}
}
