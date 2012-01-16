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
function EnergyMeter() {
	var chart;
	var minDate;
	var maxDate;
	
	var predictedSeries;
	var dataSeriesMap;
	
	var currentPlotLines;
	
	var stateTransitionTableBody;
		
	var preloadEnergyGraph = function() {
		// use last date
		reloadEnergyGraphWithDates(null, lastMeasurementDate);
	};
	
	var reloadEnergyGraphWithDates = function(start, end, callback) {
		var params = {};
		
		if(start != null) {
			params['start'] = start;
		}
		if(end != null) {
			params['end'] = end;
		}
		
		if(chart != null) {
			chart.showLoading();
		}
		
		//alert("Getting data from " + new Date(start) + " to " + new Date(end));
		
		// load grab energy data from server
		$.getJSON('/t/energy/seconds',params)
        .success(function(result) {
        	// draw energy graph
        	var secondData = result['secondData'];
        	
        	drawEnergyGraph(result);
        	showAppliances(result);
        	showStartAndEndPredictionDates(secondData);
        	showStateTransitions(result['predictedApplianceStateTransitions'], result['predictedEnergyUsageIdMap'], result['userApplianceNameMap']);
        	minDate = secondData[0][0];
        	
        	maxDate = secondData[secondData.length - 1][0];
        	//chart.hideLoading();
        })
        .error(function() {
        	alert('There was a problem generating the energy graph! We have been alerted about issue - please try again soon!');
        });
	};
	
	var showStateTransitions = function(stateTransitions, energyUsageIdMap, userApplianceNameMap) {
		stateTransitionTableBody.empty();
		
		// look through simulated appliances
		
		for(var i in stateTransitions) {
			var predictedApplianceId = energyUsageIdMap[i];
			var predictedApplianceName = userApplianceNameMap[i];
			// only do this if we haven't already seen this appliance matched with a simulated appliance
			var applianceStateTransitions = stateTransitions[i];
			
			for(var j in applianceStateTransitions) {
				var stateTransition = applianceStateTransitions[j];
				
				var newRow = $('<tr>');
				newRow.append($('<td>').html(predictedApplianceName).css('width','150px').css('border-style', 'solid').css('border-width', '1px').css('border-color', 'grey'));
				newRow.append($('<td>').html(new Date(stateTransition['time']).toLocaleString()).css('width','300px').css('border-style', 'solid').css('border-width', '1px').css('border-color', 'grey'));
				newRow.append($('<td>').html(stateTransition['on'] ? "Increase" : "Decrease").css('width','400px').css('border-style', 'solid').css('border-width', '1px').css('border-color', 'grey'));
				stateTransitionTableBody.append(newRow);
			}
		}
		
	};
	
	var showStartAndEndPredictionDates = function(secondData) {
		var startDate = new Date(secondData[0][0]);
		$('#beginEnergyPredictionTime').html(startDate.toLocaleString());
		
		var endTime = secondData[secondData.length - 1][0];
		endTime = Math.min(endTime, lastTimeIncludedInTraining);
		
		$('#endEnergyPredictionTime').html(new Date(endTime).toLocaleString());
	};
	
	var toggleDataSeries = function(name) {
		var series = chart.series[dataSeriesMap[name]];
        if (series.visible) {
            series.hide();
        } else {
            series.show();
        }
	};
	
	var showAppliances = function(data) {
		
		var applianceTable = $('#appliancetablebody');
		applianceTable.empty();
		
		// look through simulated appliances
		
		for(var i in data['predictedEnergyUsage']) {
			var predictedApplianceId = data['predictedEnergyUsageIdMap'][i];
			var predictedApplianceName = data['userApplianceNameMap'][i];
			// only do this if we haven't already seen this appliance matched with a simulated appliance
			var predictedAppliance = data['predictedEnergyUsage'][i];
			
			var newRow = $('<tr>');
			var onBox = $('<input type="checkbox" checked="checked">');
			onBox.click(function() { toggleDataSeries('predicted_' + predictedApplianceId); });
			newRow.append($('<td>').append(onBox).css('border-style', 'solid').css('border-width', '1px').css('border-color', 'grey'));
			newRow.append($('<td>').html(predictedApplianceName).css('border-style', 'solid').css('border-width', '1px').css('border-color', 'grey'));
			
			var newText = '';
			
			if(typeof(data['predictedEnergyCostTotals']) != 'undefined') {
				newText = '$' + (data['predictedEnergyCostTotals'][i]).toFixed(2);
			}
			
			newRow.append($('<td>').html(newText + ' (' + (data['predictedEnergyUsageTotals'][i] / 1000).toFixed(1) + ')').css('border-style', 'solid').css('border-width', '1px').css('border-color', 'grey'));
			
			applianceTable.append(newRow);
		}
	};
	
	var zoomOut = function() {
		// find current min and max dates to graph
		
		
		//alert("min: " + new Date(min) + "; max: " + new Date(max));
		
		// find midpoint time
		var mid = 2 * (maxDate - minDate);
		
		var newMin = minDate - mid;
		var newMax = Math.min(new Date().getTime(), maxDate + mid);
		
		//alert("newmin: " + new Date(newMin) + "; newmax: " + new Date(newMax));
		
		reloadEnergyGraphWithDates(newMin,newMax);
	};
	
	// The selection event handler
	var handleGraphExtremesChange = function(event) {
		var chart = this;
		
		if (event.xAxis) {
			var xAxis = event.xAxis[0],
			min = xAxis.min,
			max = xAxis.max;
			
			// indicate to the user that something's going on
			chart.showLoading();
			
			min = Math.round(min);
			max = Math.round(max);
			
			reloadEnergyGraphWithDates(min,max);
			
			return false;
		}
	};
	
	var constructOnOffTransitionPlotLines = function(onOffTransitionMap, min, max) {
		var lines = [];
		
		//  calc from the current min and max of the points I'm about to show
		var minDistance = new Number(5 * (max - min) / ($('#powerUsage').width()));
		
		for(var i in onOffTransitionMap) {
			var onOffList = onOffTransitionMap[i];
			

			var lastLine = null;
			var lastLineShown = false;
			var lastLineTime = 0;
			
			for(var j in onOffList) {
				var onOff = onOffList[j];
				var time = onOff['time'];
				var color = onOff['on'] == true ? 'green' : 'red';
				
				var currentLine = {
			            color: color,
			            width: 1,
			            value: time,
			            id: j
			        };
				
				var diff = (time - lastLineTime).toFixed(0);
				var passes = (diff >= minDistance);
				
				if(lastLine == null || passes == true || j == (onOffList.length - 1)) {
					//alert(diff + " >= " + minDistance + " " + passes);
					
					if(lastLine != null && lastLineShown == false && j != onOffList.length - 1) {
						lines.push(lastLine);
					}
					lines.push(currentLine);
					
					
					lastLineShown = true;
				} else {
					lastLineShown = false;
				}

				lastLine = currentLine;
				lastLineTime = time;
			}
			
		}

		
		return lines;
	};
	
	var toggleApplianceOnOffs = function() {
		if($('#showApplianceOnOffs').is(":checked")) {
			for(var i in currentPlotLines) {
				chart.xAxis[0].addPlotLine(currentPlotLines[i]);
			}
		} else {
			for(var i in currentPlotLines) {
				chart.xAxis[0].removePlotLine(currentPlotLines[i].id);
			}
		}
	};
	
	
	var drawEnergyGraph = function(allresults) {
		predictedSeries = [];
		
		dataSeriesMap = {};
		
		var id = 1;
		
		for(var i in allresults['predictedEnergyUsage']) {
			var data = allresults['predictedEnergyUsage'][i];
			var name = i;
			var predicted_id = allresults['predictedEnergyUsageIdMap'][i];
			predictedSeries.push({name : allresults['userApplianceNameMap'][i], data: data, yAxis:0});
			dataSeriesMap['predicted_' + predicted_id] = id;
			id++;
		}
		
		var allSeries = predictedSeries;
		
		currentPlotLines = constructOnOffTransitionPlotLines(allresults['predictedApplianceStateTransitions'], allresults['secondData'][0][0], allresults['secondData'][allresults['secondData'].length - 1][0]);
		
		Highcharts.setOptions({
		    global: {
		        useUTC: false
		    }
		});
		
		var options = {
			chart: {
				renderTo: 'powerUsage',
				zoomType: 'x',
				events: {
					selection: handleGraphExtremesChange
				}
			},
		    title: {
				text: 'Energy Consumption'
			},
			xAxis: {
				type: 'datetime',
				maxZoom: 14 * 24 * 3600000, // fourteen days
				title: {
					text: 'Time (5 minute box)'
				},
				plotLines: $('#showApplianceOnOffs').is(':checked') ? currentPlotLines : []
			},
			yAxis: [{ 
				title: { text: 'Energy Consumption (Watt-Hours)' },
				min: 0.6,
				startOnTick: false,
				showFirstLabel: false
			}],
			legend: {
				enabled: false
			},
			series: allSeries
		};
		
		chart = new Highcharts.Chart(options);
		
	};
	
	var init = function() {
		
		if($('#powerUsage').length > 0) {
			preloadEnergyGraph();
			$('#zoomOut').click(zoomOut);
			$('#showApplianceOnOffs').click(toggleApplianceOnOffs);
			stateTransitionTableBody = $('#stateTransitionBody');
		}
	};
	
	init();
}

$(document).ready(function() {
	new EnergyMeter();
});
