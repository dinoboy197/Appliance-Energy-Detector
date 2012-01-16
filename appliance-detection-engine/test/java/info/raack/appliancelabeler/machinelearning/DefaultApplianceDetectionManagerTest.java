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
package info.raack.appliancelabeler.machinelearning;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import info.raack.appliancedetection.common.util.DateUtils;
import info.raack.appliancelabeler.data.Database;
import info.raack.appliancelabeler.data.batch.ItemReader;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.AlgorithmResult;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.ApplianceEnergyConsumptionDetectionAlgorithm;
import info.raack.appliancelabeler.model.AlgorithmPredictions;
import info.raack.appliancelabeler.model.energymonitor.EnergyMonitor;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

public class DefaultApplianceDetectionManagerTest {
	private DefaultApplianceDetectionManager manager;
	private ApplianceEnergyConsumptionDetectionAlgorithm algorithm1;
	private ApplianceEnergyConsumptionDetectionAlgorithm algorithm2;
	private Database database;
	private DateUtils dateUtils;
	
	@Before public void setup() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
		manager = new DefaultApplianceDetectionManager();
		
		algorithm1 = mock(ApplianceEnergyConsumptionDetectionAlgorithm.class);
		when(algorithm1.getAlgorithmName()).thenReturn("algo1");
		when(algorithm1.getId()).thenReturn(1);
		algorithm2 = mock(ApplianceEnergyConsumptionDetectionAlgorithm.class);
		when(algorithm2.getAlgorithmName()).thenReturn("algo2");
		when(algorithm2.getId()).thenReturn(2);
		
		database = mock(Database.class);
		dateUtils = mock(DateUtils.class);
		
		Class<?> c = manager.getClass();
	    Field f = c.getDeclaredField("applianceStateTransitionDetectionAlgorithms");
	    f.setAccessible(true);
	    f.set(manager, Arrays.asList(new ApplianceEnergyConsumptionDetectionAlgorithm[] {algorithm1, algorithm2}));
	    
	    f = c.getDeclaredField("activeAlgorithms");
	    f.setAccessible(true);
	    f.set(manager, "algo1");
	    
	    f = c.getDeclaredField("database");
	    f.setAccessible(true);
	    f.set(manager, database);
	    
	    f = c.getDeclaredField("dateUtils");
	    f.setAccessible(true);
	    f.set(manager, dateUtils);
		
	}
	
	private void init(boolean autoRetraining, boolean skipMemoryTest) {
		try {
			Class<?> c = manager.getClass();
		    Field f = c.getDeclaredField("autoModelRetraining");
		    f.setAccessible(true);
		    f.set(manager, autoRetraining);
		    
		    c = manager.getClass();
		    f = c.getDeclaredField("env");
		    f.setAccessible(true);
		    f.set(manager, skipMemoryTest ? "junit" : "prod");
		} catch (Exception e) {
			throw new RuntimeException("Could not initialize manager", e);
		}
		
		manager.init();
	}

	@Test public void trainingWorksSuccessfully() {
		
		init(false, true);
		
		EnergyMonitor monitor = mock(EnergyMonitor.class);
		
		Date date = new Date();
		Calendar finalMeasurementTime = new GregorianCalendar();
		finalMeasurementTime.setTimeInMillis(date.getTime());
		
		Calendar yearback = (Calendar)finalMeasurementTime.clone();
		yearback.add(Calendar.YEAR, -1);
		
		ItemReader<SecondData> dataReader = mock(ItemReader.class);
		AlgorithmResult result = mock(AlgorithmResult.class);
		final AlgorithmPredictions algorithmPredictions = mock(AlgorithmPredictions.class);
		
		when(database.getLastMeasurementTimeForEnergyMonitor(monitor)).thenReturn(date);
		when(dateUtils.getPreviousFiveMinuteIncrement(finalMeasurementTime)).thenReturn(finalMeasurementTime);
		when(database.getEnergyMeasurementReaderForMonitor(monitor, yearback.getTime(), finalMeasurementTime.getTime(), (int)((finalMeasurementTime.getTimeInMillis() - yearback.getTimeInMillis()) / 1000L))).thenReturn(dataReader);
		when(algorithm1.train(monitor, dataReader)).thenReturn(result);
		when(algorithm1.calculateApplianceEnergyUsePredictions(monitor, yearback, finalMeasurementTime, dataReader)).thenReturn(algorithmPredictions);
		
		// method under test
		manager.trainPredictionModelsForMonitors(Arrays.asList(new EnergyMonitor[] {monitor}));
		
		// verifications
		verify(database, times(1)).removeStateTransitionAndEnergyPredictionsForAlgorithmAndMonitor(algorithm1, monitor);
		verify(database, times(1)).saveAlgorithmResult(result);
		verify(dataReader, times(1)).moveToBeginning();		
		verify(database, times(1)).storeAlgorithmPredictions(monitor, new HashMap<Integer,AlgorithmPredictions>() {{ put(algorithm1.getId(), algorithmPredictions); }});
		verify(dataReader, times(1)).close();
		verify(monitor, times(1)).pollUnlock();
	}

}
