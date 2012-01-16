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
<%@ page language="java" import="java.util.ArrayList, info.raack.appliancedetection.evaluation.model.Evaluation" %>
<jsp:useBean id="simulations" class="java.util.ArrayList" scope="request" />
<jsp:useBean id="algorithms" class="java.util.ArrayList" scope="request" />
<jsp:useBean id="simulationIdToPreload" class="java.lang.String" scope="request" />
<jsp:useBean id="algorithmNameToPreload" class="java.lang.String" scope="request" />
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<html>
<base href="${fn:replace(pageContext.request.requestURL, pageContext.request.requestURI, '')}${pageContext.request.contextPath}/" />
<body>
<h1>Appliance Detection Evaluation Control Panel</h1>

<button id="submitNewSimulation">Start New Simulation</button><br />

Duration: <select id="durationInSeconds"><option value="3600" />One Hour</option><option value="86400">One Day</option><option value="172800">Two Days</option><option value="604800">One Week</option><option value="1209600" selected="selected">Two Weeks</option><option value="2419200">One Month</option></select>
&nbsp;&nbsp;Number of Simulated Appliances: <input type="text" id="numAppliances" value="10" />
&nbsp;&nbsp;On/Off Labels per Simulated Appliance: <input type="text" id="onOffLabelsPerAppliance" value="4" />
&nbsp;&nbsp;Max. number of simulated appliances on concurrently: <input type="text" id="onConcurrency" value="4" />
<br />
<br />
Completed Simulations:
<select id="evaluation">
<c:forEach var="simulation" items="${simulations}">
<option value="${simulation.id}">${simulation}</option>
</c:forEach>
</select>
<select id="algorithm">
<c:forEach var="algorithm" items="${algorithms}">
<option value="${algorithm.id}">${algorithm.algorithmName}</option>
</c:forEach>
</select>
<button id="displaySimulation">Display Chosen Simulation</button>
<br />
After starting a new simulation, you will need to wait a minute or two, then reload the page before the simulation will appear in the simulations list. This is because the simulation and the algorithms which prediction energy take time to trigger.<br />
<div id="metrics" style="display: none">
<h3>Evaluation Metrics (comparable across all simulations):</h3>
<table border="1">
<tr><td><strong>Energy Consumption Prediction Accuracy</strong></td><td><strong><span id="totalEnergyAccuracy"></span>%</strong></td></tr>
<tr><td><strong>State Transition Accuracy</strong></td><td><strong><span id="stateTransitionAccuracy"></span>%</strong></td></tr>
<tr><td><strong>State Transition Precision</strong></td><td><strong><span id="stateTransitionPrecision"></span>%</strong></td></tr>
<tr><td><strong>State Transition Recall</strong></td><td><strong><span id="stateTransitionRecall"></span>%</strong></td></tr>
</table>

</div>

<div id="energyConsumption" style="height: 500px"></div>

To zoom in, click and drag horizontally across the energy graph on the section which you want to enlarge.<br />
To zoom out, <button id="zoomOut">click here</button>.
<br />
<div id="otherStatistics" style="display: none">
<strong>Other statistics (not comparable across all simulations):</strong>
<br />
<table border="1">
<tr><td><strong>Total Absolute Energy Consumption Prediction Error (across all appliances)</strong></td><td><strong><span id="totalEnergyError"></span> Kilowatt-Hours (KwH) ($<span id="totalCostError"></span> at $0.1/KwH)</strong></td></tr>
</table>
</div>

<div>
<h3>Appliances</h3>
<table id="applianceTable">
<thead><tr><th></th><th colspan="2">Predicted Appliances</th><th colspan="2">Actual Appliances</th></tr></thead>
<tbody id="appliancetablebody"></tbody>
</table>
</div>

</body>
<script type="text/javascript">var simulationIdToPreload = '${simulationIdToPreload}';</script>
<script type="text/javascript">var algorithmNameToPreload = '${algorithmNameToPreload}';</script>
<script type="text/javascript" src="js/jquery-1.5.min.js"></script>
<script type="text/javascript" src="highcharts-2.1.7/js/highcharts.js"></script>
<script type="text/javascript" src="js/evaluator.js"></script>
</html>
