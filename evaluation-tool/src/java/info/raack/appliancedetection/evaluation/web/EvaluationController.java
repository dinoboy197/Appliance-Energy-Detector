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
package info.raack.appliancedetection.evaluation.web;

import info.raack.appliancedetection.evaluation.data.Database;
import info.raack.appliancedetection.evaluation.model.Evaluation;
import info.raack.appliancedetection.evaluation.model.EvaluationGroup;
import info.raack.appliancedetection.evaluation.model.Simulation;
import info.raack.appliancedetection.evaluation.model.appliance.SimulatedAppliance;
import info.raack.appliancedetection.evaluation.service.SimulationService;
import info.raack.appliancedetection.evaluation.web.json.EvaluationWrapper;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.ApplianceEnergyConsumptionDetectionAlgorithm;
import info.raack.appliancelabeler.model.EnergyTimestep;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

@Controller
public class EvaluationController extends BaseController {
	
	@Autowired
	private SimulationService simulationService;
	
	@Autowired
	private Database evaluationDatabase;

	@Autowired
	private List<ApplianceEnergyConsumptionDetectionAlgorithm> algorithms;
	
	@RequestMapping(value = "/index.html", method = RequestMethod.GET)
	public ModelAndView showSimulationGroupControlPanel(HttpServletRequest request, HttpServletResponse response) {
		ModelMap modelMap = new ModelMap();
		
		modelMap.put("simulationGroups", evaluationDatabase.getAllSimulationGroupInfo());
		return new ModelAndView("evaluations", modelMap);
	}
	
	@RequestMapping(value = "/evaluation/{id}", method = RequestMethod.GET)
	public void getEvaluationData(@PathVariable("id") String simulationId, @RequestParam("algorithmId") int algorithmId, @RequestParam(value="start", required=false) Double startMillis, @RequestParam(value="end", required=false) Double endMillis, @RequestParam(value="ticks", required=false) Integer ticks, HttpServletRequest request, HttpServletResponse response) throws IOException {
		
		// TODO - for now, just use the naive algorithm's energy measurements.
		
		Date start = null;
		Date end = null;
		
		if(startMillis != null && endMillis != null) {
			start = new Date(startMillis.longValue());
			end = new Date(endMillis.longValue());
		}
		else if(startMillis != null && endMillis == null) {
			// if only start or end are provided, create a one day span
			Calendar c = new GregorianCalendar();
			c.setTimeInMillis(startMillis.longValue());
			start = c.getTime();
		} else if(startMillis == null && endMillis != null) {
			// if only start or end are provided, create a one day span
			Calendar c = new GregorianCalendar();
			c.setTimeInMillis(endMillis.longValue());
			end = c.getTime();
			
		}
		
		if(ticks == null) {
			ticks = 300;
		}
		
		
		
		Evaluation evaluation = simulationService.getEvaluation(algorithmId, simulationId, start, end, true);
		
		if(evaluation == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return; 	
		}
		
		EvaluationWrapper wrapper = new EvaluationWrapper(evaluation);
		
		JsonSerializer<EnergyTimestep> dateSerializer = new JsonSerializer<EnergyTimestep>() {
			// serialize date to milliseconds since epoch
			public JsonElement serialize(EnergyTimestep energyTimestep, Type me, JsonSerializationContext arg2) {
				JsonArray object = new JsonArray();
				object.add(new JsonPrimitive(energyTimestep.getStartTime().getTime()));
				object.add(new JsonPrimitive(energyTimestep.getEnergyConsumed()));
				return object;
			}};
		
		String dataJS = new GsonBuilder().registerTypeAdapter(EnergyTimestep.class, dateSerializer)
			.excludeFieldsWithModifiers(Modifier.STATIC).setExclusionStrategies(new ExclusionStrategy() {

				// skip logger
			public boolean shouldSkipClass(Class<?> clazz) {
				return clazz.equals(Logger.class);
			}

			public boolean shouldSkipField(FieldAttributes fieldAttributes) {
				// skip simulation of simulated appliance
				return (fieldAttributes.getName().equals("simulation") && fieldAttributes.getDeclaringClass() == SimulatedAppliance.class) ||
				// skip simulation second data
				(fieldAttributes.getName().equals("secondData") && fieldAttributes.getDeclaringClass() == Simulation.class) ||
				// skip endTime of energytimestep
				(fieldAttributes.getName().equals("endTime") && fieldAttributes.getDeclaringClass() == EnergyTimestep.class) ||
				// skip userAppliance, detectionAlgorithmId of appliance state transition
				((fieldAttributes.getName().equals("userAppliance") || fieldAttributes.getName().equals("detectionAlgorithmId")) && fieldAttributes.getDeclaringClass() == ApplianceStateTransition.class);
			}}).create().toJson(wrapper);
		
		response.getWriter().write(dataJS);
		response.setContentType("application/json");
	}
	
	@RequestMapping(value = "/evaluationgroup/{id}", method = RequestMethod.GET)
	public void getEvaluationGroupData(@PathVariable("id") int simulationGroupId, HttpServletRequest request, HttpServletResponse response) throws IOException {
		
		EvaluationGroup evaluationGroup = simulationService.getEvaluationGroup(simulationGroupId);
		
		if(evaluationGroup == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return; 	
		}
		
		String dataJS = new GsonBuilder().excludeFieldsWithModifiers(Modifier.STATIC).setExclusionStrategies(new ExclusionStrategy() {

			// skip logger
		public boolean shouldSkipClass(Class<?> clazz) {
			return clazz.equals(Logger.class);
		}

		public boolean shouldSkipField(FieldAttributes fieldAttributes) {
			// skip everything in evaluation except overallEnergyError or simulation
			return (!(fieldAttributes.getName().equals("stateTransitionPrecision") || fieldAttributes.getName().equals("stateTransitionRecall") || fieldAttributes.getName().equals("stateTransitionAccuracy") || fieldAttributes.getName().equals("overallAccuracy") || fieldAttributes.getName().equals("overallEnergyError") || fieldAttributes.getName().equals("simulation")) && fieldAttributes.getDeclaringClass() == Evaluation.class) ||
			// skip everything in simulation except id and durationInSeconds
			(!(fieldAttributes.getName().equals("id") || fieldAttributes.getName().equals("durationInSeconds")) && fieldAttributes.getDeclaringClass() == Simulation.class);
		}}).create().toJson(evaluationGroup);
		
		response.getWriter().write(dataJS);
		response.setContentType("application/json");
	}
}
