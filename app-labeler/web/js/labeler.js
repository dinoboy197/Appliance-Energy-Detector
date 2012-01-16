#-------------------------------------------------------------------------------
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
#-------------------------------------------------------------------------------
function Labeler() {
	var onTime = null;
	
	var sendNewUserApplianceStateTransitionLabels = function(reportedOnTime, reportedOffTime, force) {
		var error = false;
		var msg = '';
		var status = '';
		var data = {'userApplianceId': userApplianceId, 'onTime':reportedOnTime.getTime(), 'offTime':reportedOffTime.getTime(),'force':force};
		
		$.ajax({
			  type: 'POST',
			  url: '/t/energy/userappliances/labels',
			  async: false,
			  data: data,
			  error: function(jqXHR, textStatus, errorThrown) { error = true; status = jqXHR.status; msg = jqXHR.responseText; }
			});
		
		return error ? [status,msg] : null;
	};
	
	var clickOn = function() {
		onTime = new Date();
		
		$('#clickOn').attr('disabled', true);
		// wait at least one second until the off button lights up
		setTimeout(function() { $('#clickOff').attr('disabled', false); }, 1000);
	};
	
	var clickOff = function(event, force, offDate) {
		var offTime;
		if(typeof(offDate) == 'undefined' || offDate == null) {
			offTime = new Date();
		} else {
			offTime = offDate;
		}
		
		if(typeof(force) == 'undefined' || force == null) {
			force = false;
		}
		var error = sendNewUserApplianceStateTransitionLabels(onTime,offTime,force);
		
		if(error == null) {
			alert("Thank you for helping to make your energy consumption prediction improvements better! You will now be returned to the main screen.");
			window.location.href = '/t/controlpanel';
		} else if(error[0] == 400) {
			var i = 5;
			if(error[1] == 'NO_POWER_INCREASE') {
				alert("Did you turn your appliance on and then back off again? We could not detect that your appliance went on and off. Please try again by following the steps once more.");
				resetTrainingButtons();
			} else if(error[1] == 'NO_POWER_DECREASE') {
				var ok = confirm("Did your appliance turn off after you turned it on? We detected that your appliance turned on but could not identify when it turned off. If you're completely certain that your appliance turned off before you clicked the off button, then click OK. Otherwise, click Cancel and please try training again.");
				if(ok) {
					clickOff(true, offTime);
				} else {
					resetTrainingButtons();
				}
			} else if(error[1] == 'NOT_TURNED_OFF') {
				var ok = confirm("Did your appliance turn completely off before telling us that your appliance was off? If you're completely certain that your appliance was completely off before clicking the start button, and was completely off after clicking the end button, then click OK. Otherwise, click Cancel and please try training again.");
				if(ok) {
					clickOff(true, offTime);
				} else {
					resetTrainingButtons();
				}
			}
		} else if(error[0] == 500) {
			alert("Sorry, we had a problem capturing information about your appliance! Our engineers have been notified. Please try again later!");
		}
	};
	
	var resetTrainingButtons = function() {

		$('#clickOn').attr('disabled', false);
		$('#clickOff').attr('disabled', true);
	};
	
	
	var init = function() {
		$('#enterControlPanel').click(function() {
			window.location = '/t/controlpanel';
		});
		
		resetTrainingButtons();
		
		$('#clickOn').click(clickOn);
		$('#clickOff').click(clickOff);
	};
	
	init();
}

$(document).ready(function() {
	new Labeler();
});
