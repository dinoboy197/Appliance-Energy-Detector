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
<%@ page language="java"  %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="retrainingInterval" class="java.lang.String" scope="request"/>

	<script type="text/javascript">
	var lastMeasurementDate = ${lastMeasurementDate.time};
	var lastTimeIncludedInTraining = ${lastTimeIncludedInTraining.time};
    </script>
    
   	<h1>Energy Consumption</h1>
   	
   	
	<h3>In order for any appliance energy consumption predictions to appear below, you need to train the system to recognize your appliances on the <a href="/t/controlpanel">energy control panel page</a>.</h3>
	<div>
		Energy consumption predictions are updated every ${retrainingInterval} hour<c:if test="${retrainingInterval > 1}">s</c:if>.
	</div>
	
	<div style="float: left">
		<h3>Appliances</h3>
		<table id="applianceTable" style="border-style: solid; border-width: 1px">
		<thead><tr><th>Visible</th><th>Appliance Name</th><th>Predicted Energy Consumption - Dollars (Kilowatt-Hours)</th></tr></thead>
		<tbody id="appliancetablebody"></tbody>
		</table>
		<br />
		These energy consumption predictions are for the timespan between <span id="beginEnergyPredictionTime"></span> and <span id="endEnergyPredictionTime"></span>.
	</div>
	<div style="float:left">
		<h3>Legend:</h3>
		<ul>
			<li>Connected lines with dots: predicted energy consumption (5 minute blocks)</li>
			<li>Green vertical bars: Times when an appliance was thought to have increased power usage</li>
			<li>Red vertical bars: Times when an appliance was thought to have decreased power usage</li>
		</ul>
	</div>
	<div style="clear:both"></div>
	

	<h3>Energy Consumption Graph</h3>
	<div id="powerUsage" style="height: 500px"></div>
	
	To zoom in, click and drag horizontally across the energy graph on the section which you want to enlarge.<br />
	To zoom out, <button id="zoomOut">click here</button>.<br />
	To show predicted power increase/decrease markers, click here <input type="checkbox" id="showApplianceOnOffs" />
	
	<%-- WE MUST INCLUDE THIS TO BE IN COMPLIANCE WITH THE HIGHCHARTS LICENSE --%>
	This chart was generated by Highcharts, a <a href="http://shop.highsoft.com/highcharts.html">highsoft software product</a> which is not free for commerical use.
	
	
	<table style="width: 880px; border-style: solid; border-width: 1px">
		<thead><tr style="display:block; position: relative"><th style="width: 150px">Appliance Name</th><th style="width: 330px">Time</th><th style="width: 400px">Energy Consumption Increase / Decrease</th></tr></thead>
		<tbody id="stateTransitionBody" style="display:block; height: 150px; overflow: auto; width:100%"></tbody>
	</table>



	


    
