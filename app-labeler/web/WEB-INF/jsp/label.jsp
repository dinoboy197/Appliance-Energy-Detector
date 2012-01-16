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
<%@ page language="java" import="info.raack.appliancelabeler.model.UserAppliance" %>
<jsp:useBean id="userAppliance" class="info.raack.appliancelabeler.model.UserAppliance" scope="request"/>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<h3>You will be helping us to predict energy consumption by your ${userAppliance.name}.</h2>

<div>We will ask you to turn off your ${userAppliance.name}, then turn it on for a <strong>full cycle</strong>, then turn it off again. Step by step instructions are provided below.
<br />
<br />
<br />
If you do not have enough time to complete a full cycle right now, that's fine. Come back to this page whenever you have the time to spend a full cycle so that we can start generating energy consumption predictions for your appliance.
<br />
<br />
Also, to make sure that we can get the best energy consumption information about your ${userAppliance.name}, <strong>please make sure that no other appliances in your home turn on or off during this process</strong> so that we can collect the best data from this appliance by itself.
<br />
<br />
Please follow the following steps when you are ready!
<ol>
<li>Make sure that any appliances which might turn on or off or change their operation automatically (air conditioners, washing machines, dishwashers, heaters, refrigerators, for example) are turned completely off.</li>
<li>Turn off your ${userAppliance.name}.</li>
<li><button id="clickOn">Click here before turning your appliance on.</button></li>
<li><c:choose>
<c:when test="${userAppliance.appliance.description == 'Electric Space Heater'}">
Turn your space heater on (fan only) for about 10 seconds, then on each heating setting (you may only have one) for 10 seconds each, then off.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Central Air Conditioner'}">
Turn your air conditioner on and making sure chilled air is felt, then turn it off.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Window/Wall Air Conditioner'}">
Turn your air conditioner on with each fan only setting for about 10 seconds, then on a setting with cooling on for about 10 seconds, then off.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Electric Water Heater'}">
Turn your water heater on and wait until it is actively heating water. Then wait for at least 60 seconds, then turn the water heater off.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Refrigerator'}">
Turn your refrigerator on for at least 60 seconds, then off.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Freezer (separate from refrigerator)'}">
Turn your freezer on for at least 60 seconds, then off.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Clothes Washer'}">
Start a cycle of your clothes washer and wait until it is completely done.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Electric Clothes Dryer'}">
Turn on your clothes dryer for at least 60 seconds, then off.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Natural Gas Clothes Dryer'}">
Turn on your clothes dryer for at least 60 seconds, then off.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Dishwasher'}">
Start a cycle of your dishwasher and wait until it is completely done.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Electric Range/Stove/Oven'}">
Turn on your oven for 60 seconds, then turn on a single burner on your stove for 10 seconds, then turn the oven and burner off.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Microwave Oven'}">
Put a cup of water inside your microwave first. Then turn your microwave on at half (50%) power for 90 seconds, and wait until it is finished.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Television'}">
Turn your television on for 60 seconds, then turn it off.
</c:when>
<c:when test="${userAppliance.appliance.description == 'Personal Computer'}">
Turn your computer on for two minutes, then turn it off.
</c:when>
<c:otherwise>
Turn on your appliance for two minutes, then turn it off.
</c:otherwise>
</c:choose>
</li>
<li>Wait until you have finished the instructions in the previous step completely.</li>
<li><button id="clickOff" disabled="true">Click here when your appliance has turned off completely.</button></li>
</ol>

<script type='text/javascript'>var userApplianceId = ${userAppliance.id};</script>
