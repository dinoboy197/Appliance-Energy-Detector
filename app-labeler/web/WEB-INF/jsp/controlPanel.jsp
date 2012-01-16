<%---------------------------------------------------------------------------------
# This file is part of the Appliance Energy Detector, a free household appliance energy disaggregation intelligence engine and webapp.
# 
# Copyright (C) 2011,2012 Taylor Raack <traack@raack.info>
# 
# The Appliance Energy Detector is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
# 
# The Appliance Energy Detector is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Affero General Public License for more details.
# 
# You should have received a copy of the GNU Affero General Public License along with the Appliance Energy Detector.  If not, see <http://www.gnu.org/licenses/>.
# 
# According to sec. 7 of the GNU Affero General Public License, version 3, the terms of the AGPL are supplemented with the following terms:
# 
# If you modify this Program, or any covered work, by linking or combining it with any of the following programs (or modified versions of those libraries), containing parts covered by the terms of those libraries licenses, the licensors of this Program grant you additional permission to convey the resulting work:
# 
# Javabeans(TM) Activation Framework 1.1 (activation) - Common Development and Distribution License Version 1.0
# AspectJ 1.6.9 (aspectjrt and aspectjweaver) - Eclipse Public License 1.0
# EMMA 2.0.5312 (emma and emma_ant) - Common Public License Version 1.0
# JAXB Project Libraries 2.2.2 (jaxb-api, jaxb-impl, jaxb-xjc) - Common Development and Distribution License Version 1.0
# Java Standard Template Library 1.2 (jstl) - Common Development and Distribution License Version 1.0
# Java Servlet Pages API 2.1 (jsp-api) - Common Development and Distribution License Version 1.0
# Java Transaction API 1.1 (jta) - Common Development and Distribution License Version 1.0
# JavaMail(TM) 1.4.1 (mail) - Common Development and Distribution License Version 1.0
# XML Pull Parser 3 (xpp3) - Indiana University Extreme! Lab Software License Version 1.1.1
# 
# The interactive user interface of the software display an attribution notice containing the phrase "Appliance Energy Detector". Interactive user interfaces of unmodified and modified versions must display Appropriate Legal Notices according to sec. 5 of the GNU Affero General Public License, version 3, when you propagate an unmodified or modified version of the Program. In accordance with sec. 7 b) of the GNU Affero General Public License, version 3, these Appropriate Legal Notices must prominently display either a) "Initial Development by <a href='http://www.linkedin.com/in/taylorraack'>Taylor Raack</a>" if displayed in a web browser or b) "Initial Development by Taylor Raack (http://www.linkedin.com/in/taylorraack)" if displayed otherwise.
#-------------------------------------------------------------------------------%>
<%@ page language="java" import="java.util.ArrayList, info.raack.appliancelabeler.model.Appliance" %>
<jsp:useBean id="teds" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="tedsJS" class="java.lang.String" scope="request"/>
<jsp:useBean id="appliances" class="java.util.ArrayList" scope="request" />
<jsp:useBean id="userAppliances" class="java.util.ArrayList" scope="request" />
<jsp:useBean id="userApplianceJson" class="java.lang.String" scope="request" />
<jsp:useBean id="userDetails" class="info.raack.appliancelabeler.model.StepgreenUserDetails" scope="request" />
<jsp:useBean id="allUserIds" class="java.util.ArrayList" scope="request" />
<jsp:useBean id="stepgreenUrl" class="java.lang.String" scope="request" />
<jsp:useBean id="additionalTrainingsRequiredPerUserAppliance" class="java.util.HashMap" scope="request" />
<jsp:useBean id="additionalTrainingsRequired" class="java.lang.String" scope="request" />
<jsp:useBean id="predictedMonthEnergyUsage" class="java.util.HashMap" scope="request" />
<jsp:useBean id="predictedWeekEnergyUsage" class="java.util.HashMap" scope="request" />
<jsp:useBean id="predictedDayEnergyUsage" class="java.util.HashMap" scope="request" />
<jsp:useBean id="predictedMonthEnergyCost" class="java.util.HashMap" scope="request" />
<jsp:useBean id="predictedWeekEnergyCost" class="java.util.HashMap" scope="request" />
<jsp:useBean id="predictedDayEnergyCost" class="java.util.HashMap" scope="request" />
<jsp:useBean id="retrainingInterval" class="java.lang.String" scope="request"/>
<jsp:useBean id="monthStart" class="java.util.Date" scope="request"/>
<jsp:useBean id="monthEnd" class="java.util.Date" scope="request"/>
<jsp:useBean id="weekStart" class="java.util.Date" scope="request"/>
<jsp:useBean id="weekEnd" class="java.util.Date" scope="request"/>
<jsp:useBean id="dayStart" class="java.util.Date" scope="request"/>
<jsp:useBean id="dayEnd" class="java.util.Date" scope="request"/>
<jsp:useBean id="costPerKwh" class="java.lang.String" scope="request"/>
<jsp:useBean id="energyMonitorId" class="java.lang.String" scope="request" />
<jsp:useBean id="anonymousStateTransitions" class="java.util.ArrayList" scope="request" />
<jsp:useBean id="dailyApplianceUsageAverages" class="java.util.HashMap" scope="request" />
<jsp:useBean id="weeklyApplianceUsageAverages" class="java.util.HashMap" scope="request" />
<jsp:useBean id="monthlyApplianceUsageAverages" class="java.util.HashMap" scope="request" />
<jsp:useBean id="dailyApplianceCostAverages" class="java.util.HashMap" scope="request" />
<jsp:useBean id="weeklyApplianceCostAverages" class="java.util.HashMap" scope="request" />
<jsp:useBean id="monthlyApplianceCostAverages" class="java.util.HashMap" scope="request" />
<jsp:useBean id="myDate" class="java.util.Date"/>  

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<h2>Appliance Energy Consumption Monitor</h2>

<c:if test="${userDetails.trueUserId == \"track16\"}">
<h3>You are truly logged in as ${userDetails.trueUserId}.</h3>
Switch user:
<select id="newUserId">
<c:forEach var="userId" items="${allUserIds}">
<option value="${userId}">${userId}</option>
</c:forEach>
</select>
<button id="setNewUserId">Masquerade as user</button>
</c:if>


<script type="text/javascript">
<%-- list of all teds
var teds = ${tedsJS}; --%>
</script>


<c:choose>
	<c:when test="${fn:length(teds) == 0}">
<h3>Welcome! In order to begin, you need to have a home energy monitor set up and Stepgreen needs to have access to your energy monitor. Please visit <a href="${stepgreenUrl}">Stepgreen</a> to set up monitor for your energy monitor, then return here!</h2>
	</c:when>
	<c:otherwise>
	
<%-- show list of teds or not --%>
		<c:choose>

			<c:when test="${fn:length(teds) > 2}">
				<h3>Please select which TED device you wish to monitor:</h3>
				<select id="teds">
								<c:forEach var="ted" items="${teds}">
				<option value="<c:out value="${ted.id}" />"><c:out value="${ted.description}" /></option>
								</c:forEach>
				</select>
			</c:when>

			<c:otherwise>
				<%-- only one ted --%>
				<script type="text/javascript">var selectedTedId = '${teds[0].id}'; var energyMonitorId = '${energyMonitorId}';</script>
			</c:otherwise>

		</c:choose>
		<br />

<%-- are there more appliances which need training? --%>
		<c:if test="${additionalTrainingsRequired == 't'}">
			<div style="background-color:#FFBB8A">We have determined that your energy consumption predictions can be improved - <strong>please help us now by training the system about your appliances below</strong>. The following appliances need more training:
			<ul>
				<c:forEach var="appliance" items="${additionalTrainingsRequiredPerUserAppliance}">
					<c:if test="${appliance.value > 0}"><li>${appliance.key.name}</li></c:if>
				</c:forEach>
			</ul>
			</div>
		</c:if>
		
		<c:choose>
			<c:when test="${costPerKwh == '-1.0'}">
			<div style="background-color:#FFBB8A">
				You have not entered the cost for your energy. If you enter your energy cost, we can calculate the cost of operating each of your appliances.
				Energy cost per Kilowatt-Hour (KwH) (this will be a decimal number, like 0.07): <input type="text" id="costPerKwh" value="" />
			</c:when>
			<c:otherwise>
				<div>Your energy cost is: <input type="text" id="costPerKwh" value="${costPerKwh}" />
			</c:otherwise>
		</c:choose>
		<button id="updateEnergyCost">Update Energy Cost</button></div>
		<br /><br />
		<c:choose>
		
			<c:when test="${fn:length(userAppliances) > 0}">
				<script type="text/javascript">var userAppliances = ${userApplianceJson};</script>
				
				<c:if test="${fn:length(anonymousStateTransitions) > 0}">
					<div style="background-color:#FFBB8A">There appear to be appliances turning on and off connected to your energy monitor which you have not trained. To help you figure out which appliances you may want to train, here is a list of times in the last day when we've detected some appliance activity that we do not recognize:<br />
						<ul>
							<c:forEach var="anonymousStateTransition" items="${anonymousStateTransitions}">
								<c:set target="${myDate}" property="time" value="${anonymousStateTransition.time}"/>  
								<li>${myDate} - ${anonymousStateTransition.on ? "Power Increase" : "Power Decrease"}&nbsp;&nbsp;<button id="label-${anonymousStateTransition.id}">I know which appliance this is!</button></li>
							</c:forEach>
						</ul>
					</div>
				</c:if>
			
				<h4>Estimated energy consumption - <c:if test="${costPerKwh != '-1.0'}">Dollars</c:if> (Kilowatt-Hours)</h4>
				<table border="1" >
				<tr><th>Appliance</th><th colspan="2">Last Day</th><th colspan="2">Last Week</th><th colspan="2">Last Month</th></tr>
				<tr><th></th><th>Me</th><th>Average</th><th>Me</th><th>Average</th><th>Me</th><th>Average</th></tr>
				<c:forEach var="userAppliance" items="${userAppliances}">
					<tr>
						<c:set var="longKey" value="${userAppliance.id + 0}"/>
						
						<td>${userAppliance.name}</td>
							<c:choose>
								<c:when test="${ ! empty predictedMonthEnergyUsage[userAppliance]}">
									<td align="right">
									<c:if test="${ ! empty predictedDayEnergyCost[userAppliance]}"><fmt:formatNumber type="currency" value="${predictedDayEnergyCost[userAppliance]}" /></c:if> (<fmt:formatNumber type="number" maxFractionDigits="1" value="${predictedDayEnergyUsage[userAppliance] / 1000 / 3600}" />)</td><td><c:choose><c:when test="${ ! empty dailyApplianceUsageAverages[userAppliance.appliance]}"><fmt:formatNumber type="currency" value="${dailyApplianceCostAverages[userAppliance.appliance]}" /> (<fmt:formatNumber type="number" maxFractionDigits="1" value="${dailyApplianceUsageAverages[userAppliance.appliance] / 1000 / 3600}" />)</c:when><c:otherwise>$0.00 (0)</c:otherwise></c:choose></td>
									<td align="right"><c:if test="${ ! empty predictedWeekEnergyCost[userAppliance]}"><fmt:formatNumber type="currency" value="${predictedWeekEnergyCost[userAppliance]}" /></c:if> (<fmt:formatNumber type="number" maxFractionDigits="1" value="${predictedWeekEnergyUsage[userAppliance] / 1000 / 3600}" />)</td><td><c:choose><c:when test="${ ! empty weeklyApplianceUsageAverages[userAppliance.appliance]}"><fmt:formatNumber type="currency" value="${weeklyApplianceCostAverages[userAppliance.appliance]}" /> (<fmt:formatNumber type="number" maxFractionDigits="1" value="${weeklyApplianceUsageAverages[userAppliance.appliance] / 1000 / 3600}" />)</c:when><c:otherwise>$0.00 (0)</c:otherwise></c:choose></td>
									<td align="right"><c:if test="${ ! empty predictedMonthEnergyCost[userAppliance]}"><fmt:formatNumber type="currency" value="${predictedMonthEnergyCost[userAppliance]}" /></c:if> (<fmt:formatNumber type="number" maxFractionDigits="1" value="${predictedMonthEnergyUsage[userAppliance] / 1000 / 3600}" />)</td><td><c:choose><c:when test="${ ! empty monthlyApplianceUsageAverages[userAppliance.appliance]}"><fmt:formatNumber type="currency" value="${monthlyApplianceCostAverages[userAppliance.appliance]}" /> (<fmt:formatNumber type="number" maxFractionDigits="1" value="${monthlyApplianceUsageAverages[userAppliance.appliance] / 1000 / 3600}" />)</c:when><c:otherwise>$0.00 (0)</c:otherwise></c:choose>
								</c:when>
							
								<c:otherwise>
									<td align="center" colspan="6">
									<strong><c:choose><c:when test="${additionalTrainingsRequiredPerUserAppliance[userAppliance] == 0}">Please wait... building energy consumption predictions</c:when><c:otherwise><a href="/t/label/${userAppliance.id}">Train Now</a></c:otherwise></c:choose></strong>
								</c:otherwise>
							</c:choose>
						</td>
					</tr>
				</c:forEach>
				</table>
				<br />
				Please note: after training any appliance for the first time, it can take up to ${retrainingInterval} hour<c:if test="${retrainingInterval > 1}">s</c:if> until energy consumption predictions appear in the table above for that appliance.
				<br />
				<h4><a href="/t/energy/graph">View a graph of energy consumption</a></h4>

				<hr />
				<strong>Do you have any more appliances connected to your energy monitor which are not listed in the table above?</strong>
			</c:when>

			<c:otherwise>
				
				<h4>We are almost ready to start generating energy consumption predictions for you!</h4>
				We need to know a little bit about which appliances are connected to your energy monitor. Please create as many appliances as you have connected to your energy monitor (of the ones in the appliance type list below).
				<br />
			</c:otherwise>
		</c:choose>

		<br />
		<div id="applianceCreate" style="border: 1px">
		<h4>Add a new appliance</h4>
		Please select your appliance type from the menu:
		
		<select id="appliance">
				<c:forEach var="appliance" items="${appliances}">
		<option value="<c:out value="${appliance.id}" />"><c:out value="${appliance.description}" /></option>
				</c:forEach>
		</select>
		<br />
		Please give your appliance a brief description, such as "Bedroom air conditioner", or "Basement freezer"<br />
		<input type="text" id="applianceDescription" />
		
		<button id="createUserAppliance">Save appliance description</button>
		</div>
	</c:otherwise>
</c:choose>
<br />
<br />
<br />
<a href="/t/logout.do">Logout (only necessary on a public computer)</a>
