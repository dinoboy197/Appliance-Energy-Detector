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
<%@ page language="java" import="java.util.ArrayList" %>
<jsp:useBean id="simulationGroups" class="java.util.ArrayList" scope="request" />
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<html>
<base href="${fn:replace(pageContext.request.requestURL, pageContext.request.requestURI, '')}${pageContext.request.contextPath}/" />
<body>
<h1>Appliance Detection Evaluation Control Panel</h1>

<a href="t/simulationcontrolpanel">Configuration and Run individual simulation</a><br />
<br />

<button id="submitNewSimulationGroup">Start New Simulation Group</button><br />

Duration: <select id="durationInSeconds"><option value="3600" />One Hour</option><option value="86400">One Day</option><option value="172800">Two Days</option><option value="604800">One Week</option><option value="1209600" selected="selected">Two Weeks</option><option value="2419200">One Month</option></select>
&nbsp;&nbsp;Number of Simulated Appliances: <input type="text" id="numAppliances" value="10" />
&nbsp;&nbsp;On/Off Labels per Simulated Appliance: <input type="text" id="onOffLabelsPerAppliance" value="4" />
&nbsp;&nbsp;Max. number of simulated appliances on concurrently: <input type="text" id="onConcurrency" value="4" /><br />
Number of simulations in group: <input type="text" id="numberOfSimulations" value="20" />
<br />
<br />

Completed Simulation Groups:
<select id="evaluationGroup">
<c:forEach var="simulationGroup" items="${simulationGroups}">
<option value="${simulationGroup.id}">${simulationGroup}</option>
</c:forEach>
</select>

<button id="displaySimulationGroup">Display Chosen Simulation Group</button>
<br />
<br />
<strong>Energy Consumption Prediction Accuracy</strong>
<table id="energyAccuracyTable" border="1">
<thead><tr><th>Algorithm</th><th>Mean Accuracy</th><th>Median Accuracy</th><th>Std Dev of Accuracy</th><th>Individual Simulation Performance</th></tr></thead>
<tbody id="energyAccuracyTableBody"></tbody>
</table>
<br />
<br />
<strong>State Transition Prediction Accuracy</strong>
<table id="stateTransitionAccuracyTable" border="1">
<thead><tr><th>Algorithm</th><th>Mean Accuracy</th><th>Median Accuracy</th><th>Std Dev of Accuracy</th><th>Individual Simulation Performance</th></tr></thead>
<tbody id="stateTransitionAccuracyTableBody"></tbody>
</table>
<br />
<br />
<strong>State Transition Prediction Precision</strong>
<table id="stateTransitionPrecisionTable" border="1">
<thead><tr><th>Algorithm</th><th>Mean Precision</th><th>Median Precision</th><th>Std Dev of Precision</th><th>Individual Simulation Performance</th></tr></thead>
<tbody id="stateTransitionPrecisionTableBody"></tbody>
</table>
<br />
<br />
<strong>State Transition Prediction Recall</strong>
<table id="stateTransitionRecallTable" border="1">
<thead><tr><th>Algorithm</th><th>Mean Recall</th><th>Median Recall</th><th>Std Dev of Recall</th><th>Individual Simulation Performance</th></tr></thead>
<tbody id="stateTransitionRecallTableBody"></tbody>
</table>

</body>

<script type="text/javascript" src="js/jquery-1.5.min.js"></script>
<script type="text/javascript" src="highcharts-2.1.7/js/highcharts.js"></script>
<script type="text/javascript" src="js/evaluator.js"></script>
</html>
