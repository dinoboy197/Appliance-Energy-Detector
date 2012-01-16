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
package info.raack.appliancelabeler.web;

import info.raack.appliancedetection.common.util.DateUtils;
import info.raack.appliancelabeler.data.Database;
import info.raack.appliancelabeler.machinelearning.ApplianceDetectionManager;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.ApplianceEnergyConsumptionDetectionAlgorithm;
import info.raack.appliancelabeler.model.Appliance;
import info.raack.appliancelabeler.model.EnergyTimestep;
import info.raack.appliancelabeler.model.StepgreenUserDetails;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.energymonitor.EnergyMonitor;
import info.raack.appliancelabeler.model.energymonitor.Ted5000Monitor;
import info.raack.appliancelabeler.security.HttpSessionAndDatabaseOAuthRemeberMeServices;
import info.raack.appliancelabeler.security.OAuthAutomaticAuthenticationToken;
import info.raack.appliancelabeler.security.OAuthUserDetails;
import info.raack.appliancelabeler.service.DataService;
import info.raack.appliancelabeler.service.DataService.LabelResult;
import info.raack.appliancelabeler.util.ClientAPIException;
import info.raack.appliancelabeler.util.OAuthUnauthorizedException;
import info.raack.appliancelabeler.web.json.EnergyWrapper;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;
import edu.cmu.hcii.stepgreen.data.teds.Mtu;
import edu.cmu.hcii.stepgreen.data.teds.Ted5000;

@Controller
public class MainController extends BaseController {
	
	@Autowired
	private Database database;
	
	@Autowired
	private DataService dataService;
	
	@Autowired
	private RememberMeServices rememberMeServices;
	
	@Value("${stepgreen.basehost}")
	private String stepgreenBasehost;
	
	@Value("${stepgreen.url}")
	private String stepgreenUrl;
	
	@Value("${energylabeler.url}")
	private String energyLabelerUrl;
	
	@Value("${active.algorithms}")
	private String activeAlgorithms;
	
	@Autowired
	private ApplianceDetectionManager applianceDetectionEngine;
	
	@Autowired
	private DateUtils dateUtils;
	
	@Autowired
	private List<ApplianceEnergyConsumptionDetectionAlgorithm> algorithms;
	
	private ApplianceEnergyConsumptionDetectionAlgorithm algorithm;
	
	@PostConstruct
	public void init() {
		// Spring ensure that we can't get here unless there is at least one algorithm in the algorithms list
		for(ApplianceEnergyConsumptionDetectionAlgorithm algorithm : algorithms) {
			String className = algorithm.getAlgorithmName();
			if(activeAlgorithms.contains(className)) {
				this.algorithm = algorithm;
			}
		}
		if(algorithm == null) {
			throw new RuntimeException("No algorithms available on the classpath are registered are in the active algorithms list");
		}
	}
	
	@RequestMapping(value = "/index.html", method = RequestMethod.GET)
	public ModelAndView showIndexPage() {
		ModelMap model = new ModelMap();
		model.put("url", stepgreenUrl);
		return templateProvider.showPageInTemplate(1, "main", model);
	}
	
	@RequestMapping(value = "/controlpanel", method = RequestMethod.GET)
	public ModelAndView showControlPanel(HttpServletRequest request, HttpServletResponse response) {
		ModelMap model = new ModelMap();
		
		String userId = getUserId(request, response, false);
		if(userId != null) {
			List<Appliance> appliances = database.getAllAppliances();
			
			// get information for currently selected energy monitor
			EnergyMonitor energyMonitor = getCurrentEnergyMonitor(request, response);
			List<UserAppliance> userAppliances = new ArrayList<UserAppliance>();
			Map<UserAppliance,Integer> additionalTrainingsRequiredPerUserAppliance = null;
			Map<UserAppliance,Double> predictedMonthEnergyUsage = new HashMap<UserAppliance, Double>();
			Map<UserAppliance,Double> predictedWeekEnergyUsage = new HashMap<UserAppliance, Double>();
			Map<UserAppliance,Double> predictedDayEnergyUsage = new HashMap<UserAppliance, Double>();
			Map<UserAppliance,Double> predictedMonthEnergyCost = new HashMap<UserAppliance, Double>();
			Map<UserAppliance,Double> predictedWeekEnergyCost = new HashMap<UserAppliance, Double>();
			Map<UserAppliance,Double> predictedDayEnergyCost = new HashMap<UserAppliance, Double>();
			
			
			if(energyMonitor != null) {
				userAppliances = database.getUserAppliancesFromUserForEnergyMonitor(energyMonitor);
				additionalTrainingsRequiredPerUserAppliance = applianceDetectionEngine.getAdditionalTrainingsRequiredPerUserAppliance(energyMonitor);
				
				Date dayQueryStart = new Date();
				Date weekQueryStart = new Date();
				Date monthQueryStart = new Date();
				Date queryEnd = new Date();
				
				float costPerKwh = database.getEnergyCost(energyMonitor);

				createOneMonthSpan(energyMonitor, monthQueryStart, queryEnd);
				model.put("monthStart", monthQueryStart);
				model.put("monthEnd", queryEnd);
				Map<UserAppliance, List<EnergyTimestep>> predictedMonthEnergyUsageTimesteps = dataService.getApplianceEnergyConsumptionForMonitor(energyMonitor, algorithm.getId(), monthQueryStart, queryEnd);
				extractTotalEnergyConsumption(predictedMonthEnergyUsageTimesteps, costPerKwh, predictedMonthEnergyUsage, predictedMonthEnergyCost);
				
				createOneWeekSpan(energyMonitor, weekQueryStart, queryEnd);
				model.put("weekStart", weekQueryStart);
				model.put("weekEnd", queryEnd);
				Map<UserAppliance, List<EnergyTimestep>> predictedWeekEnergyUsageTimesteps = dataService.getApplianceEnergyConsumptionForMonitor(energyMonitor, algorithm.getId(), weekQueryStart, queryEnd);
				extractTotalEnergyConsumption(predictedWeekEnergyUsageTimesteps, costPerKwh, predictedWeekEnergyUsage, predictedWeekEnergyCost);
				
				
				queryEnd = new Date();
				createOneDaySpan(energyMonitor, dayQueryStart, queryEnd);
				model.put("dayStart", dayQueryStart);
				model.put("dayEnd", queryEnd);
				Map<UserAppliance, List<EnergyTimestep>> predictedDayEnergyUsageTimesteps = dataService.getApplianceEnergyConsumptionForMonitor(energyMonitor, algorithm.getId(), dayQueryStart, queryEnd);
				extractTotalEnergyConsumption(predictedDayEnergyUsageTimesteps, costPerKwh, predictedDayEnergyUsage, predictedDayEnergyCost);
			
				// add anonymous unlabeled state transitions
				List<ApplianceStateTransition> anonymousTransitions = database.getAnonymousApplianceStateTransitionsForMonitor(energyMonitor, algorithm.getId(), dayQueryStart, queryEnd);
				
				if(anonymousTransitions.size() > 0) {
					model.put("anonymousStateTransitions", new ArrayList<ApplianceStateTransition> (anonymousTransitions.subList(Math.max(0, anonymousTransitions.size() - 10), anonymousTransitions.size())));
				}
				
				addGlobalUsageComparisonsPerAppliance(model, energyMonitor, costPerKwh, monthQueryStart, weekQueryStart, dayQueryStart);
				
				model.put("costPerKwh", costPerKwh + "");
				model.put("energyMonitorId", energyMonitor.getId() + "");
				
			} else {
				model.put("costPerKwh", "-1.0");
			}
			
			
			final List<Ted5000> teds = dataService.getTEDIdsForUserId(userId, false);
			String tedsJS = new GsonBuilder().create().toJson(teds, new TypeToken<List<Ted5000>>() {}.getType());
			
			model.put("teds", new ArrayList<Ted5000>() {{ addAll(teds); }});
			model.put("tedsJS", tedsJS);
			model.put("appliances", appliances);
			model.put("userAppliances", userAppliances);
			model.put("userApplianceJson", new GsonBuilder().create().toJson(userAppliances));
			model.put("additionalTrainingsRequiredPerUserAppliance", additionalTrainingsRequiredPerUserAppliance);
			model.put("additionalTrainingsRequired", areAdditionalTrainingsRequired(additionalTrainingsRequiredPerUserAppliance));
			model.put("predictedMonthEnergyUsage", predictedMonthEnergyUsage);
			model.put("predictedWeekEnergyUsage", predictedWeekEnergyUsage);
			model.put("predictedDayEnergyUsage", predictedDayEnergyUsage);
			model.put("predictedMonthEnergyCost", predictedMonthEnergyCost);
			model.put("predictedWeekEnergyCost", predictedWeekEnergyCost);
			model.put("predictedDayEnergyCost", predictedDayEnergyCost);
			model.put("stepgreenUrl", stepgreenUrl);
			model.put("retrainingInterval", applianceDetectionEngine.getRetrainingIntervalInHours() + "");
			
			return templateProvider.showPageInTemplate(1, "controlPanel", model);
		} else {
			throw new RuntimeException("No user id!");
		}
	}

	private void addGlobalUsageComparisonsPerAppliance(ModelMap model, EnergyMonitor monitor, double costPerKwh, Date monthQueryStart, Date weekQueryStart, Date dayQueryStart) {
		// daily comparison
		Map<Appliance, Double> dailyAverages = database.getApplianceEnergyConsumptionAverages(dayQueryStart, algorithm.getId(), monitor);
		model.put("dailyApplianceUsageAverages", dailyAverages);
		model.put("dailyApplianceCostAverages", convertToCostAverages(dailyAverages, costPerKwh));
		
		// weekly comparison
		Map<Appliance, Double> weeklyAverages = database.getApplianceEnergyConsumptionAverages(weekQueryStart, algorithm.getId(), monitor);
		model.put("weeklyApplianceUsageAverages", weeklyAverages);
		model.put("weeklyApplianceCostAverages", convertToCostAverages(weeklyAverages, costPerKwh));
		
		// monthly comparison
		Map<Appliance, Double> monthlyAverages = database.getApplianceEnergyConsumptionAverages(monthQueryStart, algorithm.getId(), monitor);
		model.put("monthlyApplianceUsageAverages", monthlyAverages);
		model.put("monthlyApplianceCostAverages", convertToCostAverages(monthlyAverages, costPerKwh));
	}

	private Map<Appliance,Double> convertToCostAverages(Map<Appliance, Double> dailyAverages, double costPerKwh) {
		Map<Appliance,Double> newMap = new HashMap<Appliance,Double>();
		
		for(Entry<Appliance,Double> entry : dailyAverages.entrySet()) {
			newMap.put(entry.getKey(), costPerKwh * entry.getValue() / 1000.0 / 3600.0);
		}
		
		return newMap;
	}

	private void extractTotalEnergyConsumption(Map<UserAppliance, List<EnergyTimestep>> predictedEnergyUsageTimesteps, float costPerKwh, Map<UserAppliance, Double> predictedEnergyUsage, Map<UserAppliance, Double> predictedEnergyCost) {

		for(UserAppliance app : predictedEnergyUsageTimesteps.keySet()) {
			Double total = 0d;
			for(EnergyTimestep timestep : predictedEnergyUsageTimesteps.get(app)) {
				total += timestep.getEnergyConsumed();
			}
			predictedEnergyUsage.put(app, total);
			if(costPerKwh > 0) {
				predictedEnergyCost.put(app, (double)total * costPerKwh / (double)1000 / (double)3600);
			}
		}
	}
	
	private String areAdditionalTrainingsRequired(Map<UserAppliance, Integer> additionalTrainingsRequiredPerUserAppliance) {
		
		if(additionalTrainingsRequiredPerUserAppliance != null) {
			for(UserAppliance app : additionalTrainingsRequiredPerUserAppliance.keySet()) {
				if(additionalTrainingsRequiredPerUserAppliance.get(app) > 0) {
					return "t";
				}
	 		}
		}
		return "f";
	}

	@RequestMapping(value = "/currentuser", method = RequestMethod.POST)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void masqueradeAsUser(@RequestParam("userId") String newUserId, HttpServletRequest request, HttpServletResponse response) {
		String userId = getUserId(request, response, true);
		
		// TODO - remove hardcoding for me, put admin login in property file
		if(userId != null && userId.equals("track16")) {
			userDetails.setMasqueradeUserId(newUserId);
		} else {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}
			

	private String getUserId(HttpServletRequest request, HttpServletResponse response, boolean trueId) {
		// extract userid from spring security
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		
		if((trueId && userDetails.getTrueUserId() == null) || (!trueId && userDetails.getEffectiveUserId() == null)) {
			if(auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
				// user is already logged in via spring security
				String userId = null;
				if(auth instanceof RememberMeAuthenticationToken) {
					userId = ((OAuthUserDetails)auth.getPrincipal()).getUsername();
				} else {
					userId = (String)auth.getPrincipal();
				}
				userDetails.setUserId(userId);
				return userId;
				
			} else if(auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
				logger.info("User is not logged in, so let's get their info by accessing the stepgreen service userinfo uri and forcing a login");
				StepgreenUserDetails capturedDetails = null;
				try {
					capturedDetails = dataService.getStepgreenUserInfo();
					logger.debug("Got user id: " + capturedDetails.getTrueUserId());
					OAuthAutomaticAuthenticationToken token = new OAuthAutomaticAuthenticationToken(capturedDetails.getTrueUserId());
					
					// generate session if one does not exist
					request.getSession();
					SecurityContextHolder.getContext().setAuthentication(token);
					request.getSession().setAttribute(
				      HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
				      SecurityContextHolder.getContext());
					
					// add email to session, so that the remember me services can remember it
					request.getSession().setAttribute(HttpSessionAndDatabaseOAuthRemeberMeServices.EMAIL_ATTRIBUTE, capturedDetails.getEmail());
					
					// remember the new authentication
					rememberMeServices.loginSuccess(request, response, token);
					
					userDetails.setUserId(capturedDetails.getTrueUserId());
					return capturedDetails.getTrueUserId();
					
				} catch (Exception e) {
					throw new RuntimeException("Could not get user id from stepgreen", e);
				}
			} else {
				throw new RuntimeException("Could not get user id");
			}
		} else {
			return userDetails.getEffectiveUserId();
		}
	}
	
	@RequestMapping(value = "/energy/graph", method = RequestMethod.GET)
	public ModelAndView showEnergyGraph(HttpServletRequest request, HttpServletResponse response) {
		EnergyMonitor energyMonitor = getCurrentEnergyMonitor(request, response);
		
		ModelMap model = new ModelMap();
		model.put("lastMeasurementDate", new Date());
		model.put("lastTimeIncludedInTraining", applianceDetectionEngine.getLastTimeIncludedInTraining(energyMonitor));
		model.put("retrainingInterval", applianceDetectionEngine.getRetrainingIntervalInHours() + "");
		
		if(energyMonitor != null) {
			Date lastTime = database.getLastMeasurementTimeForEnergyMonitor(energyMonitor);
			
			model.put("lastMeasurementDate", lastTime);
		}
		return templateProvider.showPageInTemplate(1, "meter", model);
	}
	
	/*@RequestMapping(value = "/energy/delete", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteSession(HttpServletRequest request) {
		request.getSession().invalidate();
	}*/
	
	@RequestMapping(value = "/energy/{frequency}", method = RequestMethod.GET)
	public void getEnergyData(@PathVariable("frequency") String frequency, @RequestParam(value="start", required=false) Double startMillis, @RequestParam(value="end", required=false) Double endMillis, @RequestParam(value="ticks", required=false) Integer ticks, HttpServletRequest request, HttpServletResponse response) throws IOException {
		// need to get latest values from stepgreen service
		
		EnergyMonitor energyMonitor = getCurrentEnergyMonitor(request, response);
		
		Date start = new Date();
		Date end = new Date();
		
		if(startMillis != null && endMillis != null) {
			start = new Date(startMillis.longValue());
			end = new Date(endMillis.longValue());
		}
		else if(startMillis != null && endMillis == null) {
			// if only start or end are provided, create a one day span
			Calendar c = new GregorianCalendar();
			c.setTimeInMillis(startMillis.longValue());
			start = new Date();
			start.setTime(startMillis.longValue());
			
			c.add(Calendar.DATE, 1);
			end = c.getTime();
		} else if(startMillis == null && endMillis != null) {
			// if only start or end are provided, create a one day span
			Calendar c = new GregorianCalendar();
			c.setTimeInMillis(endMillis.longValue());
			end = new Date();
			end.setTime(endMillis.longValue());
			
			c.add(Calendar.DATE, -1);
			start = c.getTime();
			
		} else {
			createOneDaySpan(energyMonitor, start, end);
		}
		
		if(ticks == null) {
			ticks = 300;
		}
		
		Date queryStart = null;
		Date queryEnd = null;
		
		// if the time window is less than 5 minutes, then just take the window as is; otherwise, enlarge the window to the 5 minute interval requested
		if(end.getTime() - start.getTime() > (5 * 60 * 1000)) {
			Calendar cal = new GregorianCalendar();
			cal.setTime(start);
			queryStart = dateUtils.getPreviousFiveMinuteIncrement(cal).getTime();
	
			cal = new GregorianCalendar();
			cal.setTime(end);
			queryEnd = dateUtils.getNextFiveMinuteIncrement(cal).getTime();
		}
		else {
			queryStart = start;
			queryEnd = end;
		}
		
		List<SecondData> data = getEnergyDataWithLimits(energyMonitor, DataService.DataFrequency.valueOf(frequency.toUpperCase()), queryStart, queryEnd, ticks);
		Map<UserAppliance, List<EnergyTimestep>> predictedEnergyUsage = dataService.getApplianceEnergyConsumptionForMonitor(energyMonitor, algorithm.getId(), queryStart, queryEnd);
		Map<UserAppliance, List<ApplianceStateTransition>> predictedApplianceStateTransitions = dataService.getPredictedApplianceStateTransitionsForMonitor(energyMonitor, algorithm.getId(), queryStart, queryEnd);
		
		JsonSerializer<EnergyTimestep> dateSerializer = new JsonSerializer<EnergyTimestep>() {
			// serialize date to milliseconds since epoch
			public JsonElement serialize(EnergyTimestep energyTimestep, Type me, JsonSerializationContext arg2) {
				JsonArray object = new JsonArray();
				object.add(new JsonPrimitive(energyTimestep.getStartTime().getTime()));
				object.add(new JsonPrimitive(energyTimestep.getEnergyConsumed()));
				return object;
			}};
			
		EnergyWrapper energyWrapper = new EnergyWrapper(data, predictedEnergyUsage, predictedApplianceStateTransitions, database.getEnergyCost(energyMonitor));
			
		String dataJS = new GsonBuilder().registerTypeAdapter(EnergyTimestep.class, dateSerializer)
		.excludeFieldsWithModifiers(Modifier.STATIC).setExclusionStrategies(new ExclusionStrategy() {

			// skip logger
		public boolean shouldSkipClass(Class<?> clazz) {
			return clazz.equals(Logger.class);
		}

		public boolean shouldSkipField(FieldAttributes fieldAttributes) {
			// skip endTime of energytimestep
			return (fieldAttributes.getName().equals("endTime") && fieldAttributes.getDeclaringClass() == EnergyTimestep.class) ||
			// skip userAppliance, detectionAlgorithmId of appliance state transition
			((fieldAttributes.getName().equals("userAppliance") || fieldAttributes.getName().equals("detectionAlgorithmId")) && fieldAttributes.getDeclaringClass() == ApplianceStateTransition.class);
		}}).create().toJson(energyWrapper);
		
		response.getWriter().write(dataJS);
		
		// set appropriate JSON response type
		response.setContentType("application/json");
	}

	private void createOneDaySpan(EnergyMonitor energyMonitor, Date start, Date end) {
		int period = Calendar.DATE;
		
		createDateSpan(energyMonitor, start, end, period);
	}
	
	private void createOneWeekSpan(EnergyMonitor energyMonitor, Date start, Date end) {
		int period = Calendar.WEEK_OF_MONTH;
		
		createDateSpan(energyMonitor, start, end, period);
	}
	
	private void createOneMonthSpan(EnergyMonitor energyMonitor, Date start, Date end) {
		int period = Calendar.MONTH;
		
		createDateSpan(energyMonitor, start, end, period);
	}

	private void createDateSpan(EnergyMonitor energyMonitor, Date start, Date end, int period) {
		Calendar c = new GregorianCalendar();
		Date tempDate = null;
		
		if(energyMonitor != null) {
			tempDate = database.getLastMeasurementTimeForEnergyMonitor(energyMonitor);
		}
		
		if(tempDate == null) {
			end.setTime(System.currentTimeMillis());
		} else {
			end.setTime(tempDate.getTime());
		}
		
		c.setTime(end);
		
		c.add(period, -2);
		start.setTime(c.getTimeInMillis());
	}

	private EnergyMonitor getCurrentEnergyMonitor(HttpServletRequest request, HttpServletResponse response) {
		String userId = getUserId(request, response, false);
		
		List<Ted5000> teds = dataService.getTEDIdsForUserId(userId, false);
		
		// TODO - allow user to select the Ted / MTU combination that they want to use, put it in the path
		// right now, just select the first ted and mtu
		if(teds.size() > 0) {
			Ted5000 ted = teds.get(0);
			if(ted.getFetch() != null && ted.getFetch().getFetch() != null && ted.getFetch().getFetch().equals("true")) {
				if(ted.getTed5000Mtu().size() > 0) {
					Mtu mtu = ted.getTed5000Mtu().get(0);
					
					Ted5000Monitor energyMonitor = new Ted5000Monitor(-1, userId, ted.getId(), mtu.getId(), stepgreenBasehost);
					EnergyMonitor dbMonitor = database.getEnergyMonitor(userId, energyMonitor.getMonitorId(), energyMonitor.getType());
					
					return dbMonitor != null ? dbMonitor : energyMonitor;
				}
			}
		}
		
		return null;
	}
	
	private List<SecondData> getEnergyDataWithLimits(EnergyMonitor energyMonitor, DataService.DataFrequency frequency, Date start, Date end, int ticks) {
		logger.info("Energy data requested: " + energyMonitor + "_" + frequency + ", starting at " + start + " ending at " + end + ", one tick every " + frequency + " seconds");
		return database.getEnergyMeasurementsForMonitor(energyMonitor, start, end, ticks);
	}
	
	@RequestMapping(value = "/energy/userappliances", method = RequestMethod.POST)
	public void addUserAppliance(@RequestParam(value="applianceId") int applianceId, @RequestParam(value="name") String name, HttpServletRequest request, HttpServletResponse response) throws IOException {
		ModelMap model = new ModelMap();
		
		// get current energymonitor
		EnergyMonitor energyMonitor = getCurrentEnergyMonitor(request, response);
		
		Appliance appliance = new Appliance();
		appliance.setId(applianceId);
		UserAppliance userAppliance = new UserAppliance(-1, appliance, name, -1, false);
		database.addUserAppliance(energyMonitor, userAppliance);
		
		model.put("userappliance", userAppliance);
		
		String dataJS = new GsonBuilder().create().toJson(userAppliance);
		
		response.getWriter().write(dataJS);
		
		// set appropriate JSON response type
		response.setContentType("application/json");
	}
	

	@RequestMapping(value = "/label/{id}", method = RequestMethod.GET)
	public ModelAndView showLabelPage(@PathVariable(value="id") int userApplianceId) {
		
		ModelMap model = new ModelMap();
		UserAppliance userAppliance = database.getUserApplianceById(userApplianceId);
		if(userAppliance == null) {
			throw new IllegalArgumentException("No user appliance for id " + userApplianceId);
		}
		model.put("userAppliance", userAppliance);
		
		return templateProvider.showPageInTemplate(1, "label", model);
	}
	
	/**
	 * HTTP request handler for adding new appliance label for previously identified state transition
	 * @throws IOException 
	 */
	@RequestMapping(value = "/energy/transition/{id}/labels", method = RequestMethod.POST)
	public void addAnonymousUserApplianceLabel(@PathVariable(value="id") int transitionId, @RequestParam(value="userApplianceId") int userApplianceId, HttpServletRequest request, HttpServletResponse response) throws IOException {

		// get current energymonitor
		EnergyMonitor energyMonitor = getCurrentEnergyMonitor(request, response);
		
		// generate transition
		dataService.createUserGeneratedLabelsSurroundingAnonymousTransition(transitionId, userApplianceId);
		
		response.setStatus(HttpServletResponse.SC_CREATED);
	}
	
	
	/**
	 * HTTP request handler for adding new appliance labels
	 * @throws IOException 
	 */
	@RequestMapping(value = "/energy/userappliances/labels", method = RequestMethod.POST)
	public void addUserApplianceLabel(@RequestParam(value="userApplianceId") int applianceId, @RequestParam(value="onTime") long onTime, @RequestParam(value="offTime") long offTime, @RequestParam(value="force") boolean force, HttpServletRequest request, HttpServletResponse response) throws IOException {

		// get current energymonitor
		EnergyMonitor energyMonitor = getCurrentEnergyMonitor(request, response);
		
		// TODO - set the forth argument back to 'force' once we can guarantee that the dataservice gets up to the second data for the ted on demand to correct detect current spikes
		LabelResult result = dataService.createUserGeneratedLabelsForUserApplianceId(energyMonitor, applianceId, new Date(onTime), new Date(offTime), true);
		
		if(result == LabelResult.OK) {
			logger.info("Found acceptable user labels");
			response.setStatus(HttpServletResponse.SC_CREATED);
		} else {
			logger.info("Could not find acceptable user labels");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().write(result.toString());
		}
	}
	
	/**
	 * HTTP request handler for updating energy cost for an energy monitor.
	 * @throws IOException 
	 */
	@RequestMapping(value = "/energy/monitor/{monitorId}", method = RequestMethod.POST)
	public void updateEnergyCost(@PathVariable(value="monitorId") int energyMonitorId, @RequestParam(value="costPerKwh") String costPerKwhStr, HttpServletRequest request, HttpServletResponse response) throws IOException {
		ModelMap model = new ModelMap();
		
		// get current energymonitor
		// TODO - this should be updated to allow for multiple monitors
		EnergyMonitor energyMonitor = getCurrentEnergyMonitor(request, response);
		
		if(energyMonitor == null) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		else if(energyMonitor.getId() != energyMonitorId) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().write("First energy monitor id " + energyMonitor.getId() + " does not match monitor id given of " + energyMonitorId);
			return;
		}
		
		try {
			float costPerKwh = Float.parseFloat(costPerKwhStr);
			database.setEnergyCost(energyMonitor, costPerKwh);
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
		} catch (NumberFormatException e) {
			// could not parse number
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().write("Could not parse " + costPerKwhStr + " into a number");
		}
		
	}
	
	@RequestMapping(value = "/energydata", method = RequestMethod.GET)
	public void getEnergyForUser(@RequestParam(value="userId") String userId, @RequestParam(value="start", required=false) Double startMillis, @RequestParam(value="end", required=false) Double endMillis, HttpServletResponse response) throws IOException {
		
		logger.debug("Serving request for user info for " + userId);
		
		// get first energy monitor for user
		List<Ted5000> teds = null;
		int retry = 3;
		while(true) {
			try {
				teds = dataService.getTEDIdsForUserId(userId, true);
				break;
			} catch (OAuthUnauthorizedException e) {
				throw new ClientAPIException("User " + userId + " has not authorized use of their Stepgreen data by accessing and logging into " + energyLabelerUrl + "t/controlpanel first", e);
			} catch (Exception e) {
				// retry
				if(retry-- == 0) {
					throw new RuntimeException("Could not access energy data for user id " + userId + " after 3 attempts", e);
				}
			}
		}
		
		// TODO - allow user to select the Ted / MTU combination that they want to use, put it in the path
		// right now, just select the first ted and mtu
		if(teds.size() > 0) {
			Ted5000 ted = teds.get(0);
			if(ted.getTed5000Mtu().size() > 0) {
				Mtu mtu = ted.getTed5000Mtu().get(0);
				
				EnergyMonitor energyMonitor = new Ted5000Monitor(-1, userId, ted.getId(), mtu.getId(), "test");
				
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
					start = new Date();
					start.setTime(startMillis.longValue());
					
					c.add(Calendar.DATE, 1);
					end = c.getTime();
				} else if(startMillis == null && endMillis != null) {
					// if only start or end are provided, create a one day span
					Calendar c = new GregorianCalendar();
					c.setTimeInMillis(endMillis.longValue());
					end = new Date();
					end.setTime(endMillis.longValue());
					
					c.add(Calendar.DATE, -1);
					start = c.getTime();
					
				} else {
					//  create a one day span
					if(energyMonitor != null) {
						end = database.getLastMeasurementTimeForEnergyMonitor(energyMonitor);
					}
					
					if(end == null) {
						end = new Date();
					}
					
					Calendar c = new GregorianCalendar();
					c.setTime(end);
					
					c.add(Calendar.DATE, -1);
					start = c.getTime();
				}
				
				Calendar cal = new GregorianCalendar();
				cal.setTime(start);
				Date queryStart = dateUtils.getPreviousFiveMinuteIncrement(cal).getTime();

				cal = new GregorianCalendar();
				cal.setTime(end);
				Date queryEnd = dateUtils.getNextFiveMinuteIncrement(cal).getTime();
				
				
				Map<UserAppliance, List<EnergyTimestep>> predictedEnergyUsage = dataService.getApplianceEnergyConsumptionForMonitor(energyMonitor, algorithm.getId(), queryStart, queryEnd);
				

				Map<String,Double> result = new HashMap<String,Double>();
				
				for(UserAppliance app : predictedEnergyUsage.keySet()) {
					Double total = 0d;
					for(EnergyTimestep timestep : predictedEnergyUsage.get(app)) {
						total += timestep.getEnergyConsumed();
					}
					result.put(app.getName(), total);
				}
				
				String dataJS = new GsonBuilder().create().toJson(result);
				
				response.getWriter().write(dataJS);
				
				// set appropriate JSON response type
				response.setContentType("application/json");
				
				return;
			}
		}
		
		throw new ClientAPIException("No ted monitors found for user " + userId);
	
	}
}
