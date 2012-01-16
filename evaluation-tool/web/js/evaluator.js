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
function ControlPanel() {
	var chart;
	var currentSimulationId;
	var minDate;
	var maxDate;
	var currentEvaluationData;
	var totalEnergyError;
	var totalEnergyAccuracy;
	var averageEnergyError;
	var applianceTable;
	var metricsEl;
	var otherStatistics;
	var dataSeriesMap;
	var algorithmEl;
	var totalCostErrorEl;
	
	var evaluationTableBodyEl;
	var energyAccuracyTableBodyEl;
	
	var simulatedSeries;
	var predictedSeries;
	
	var series;
		
	var displaySimulation = function() {
		// use last date
		var simulationId = $('#evaluation').val();
		if(simulationId != undefined && simulationId != '') {
			currentSimulationId = simulationId;
			loadSimulationEnergyGraph();
		}
	};
	
	var loadSimulationEnergyGraph = function(start, end, callback) {
		var params = {algorithmId: algorithmEl.val()};
		
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
		$.getJSON('t/evaluation/' + currentSimulationId,params)
        .success(function(data) {
        	// draw energy graph
        	currentEvaluationData = data;
        	drawEnergyGraph(data);
        	
        	showError(data);
        	showAppliances(data);
        	metricsEl.show();
        	otherStatistics.show();
        	
        	minDate = data['secondData'][0][0];
        	
        	maxDate = data['secondData'][data['secondData'].length - 1][0];
        	//chart.hideLoading();
        })
        .error(function() {
        	alert('There was a problem generating the energy graph!');
        });
	};
	
	var showError = function(data) {
		var totalEnergyErr = data['overallEnergyError'] / 1000;
		//totalEnergyError.html(totalEnergyErr.toFixed(1));
		totalEnergyAccuracy.html((data['overallAccuracy']).toFixed(0));
		stateTransitionAccuracy.html((data['stateTransitionAccuracy']).toFixed(0));
		stateTransitionRecall.html((data['stateTransitionRecall']).toFixed(0));
		stateTransitionPrecision.html((data['stateTransitionPrecision']).toFixed(0));
		totalCostErrorEl.html((totalEnergyErr * 0.1).toFixed(2));
		
		var durationInSeconds = data['simulation']['durationInSeconds'];
		var averageError = data['overallEnergyError'] / (durationInSeconds / 3600);
		averageEnergyError.html(averageError.toFixed(1));
	};
	
	var toggleDataSeries = function(name) {
		var series = chart.series[dataSeriesMap[name]];
        if (series.visible) {
            series.hide();
        } else {
            series.show();
        }
	}
	
	var showAppliances = function(data) {
		var predictedApplianceIdsShown = [];
		
		var applianceTable = $('#appliancetablebody');
		applianceTable.empty();
		
		// look through simulated appliances
		for(var i in data['simulation']['appliances']) {
			var simulatedAppliance = data['simulation']['appliances'][i];
		
			var newRow = $('<tr>');
			newRow.append($('<td>')); //.append($('<input type="checkbox">')));
			
			if(simulatedAppliance['labeledAppliance'] != undefined) {
				// found a matched predicted appliance - show it
				predictedApplianceIdsShown.push(simulatedAppliance['labeledAppliance']['id']);
				var onBox = $('<input type="checkbox" checked="checked">');
				onBox.click((function(name) { return function() { toggleDataSeries('predicted_' + name); };})(simulatedAppliance['labeledAppliance']['id']));
				newRow.append($('<td>').append(onBox));
				newRow.append($('<td>').html('predicted Appliance'));
				
			} else {
				// no matched predicted appliance
				newRow.append($('<td></td>'));
				newRow.append($('<td></td>'));
			}
			
			var onBox = $('<input type="checkbox" checked="checked">');
			onBox.click((function(name) { return function() { toggleDataSeries('simulated_' + name); };})(i));
			newRow.append($('<td>').append(onBox));
			newRow.append($('<td>').html(simulatedAppliance['name']));
			
			applianceTable.append(newRow);
		}
		
		for(var i in data['predictedEnergyUsage']) {
			var predictedApplianceId = data['predictedEnergyUsageIdMap'][i];
			if($.inArray(predictedApplianceId, predictedApplianceIdsShown) == -1) {
				alert(predictedApplianceId + " is not in " + predictedApplianceIdsShown);
				// only do this if we haven't already seen this appliance matched with a simulated appliance
				var predictedAppliance = data['predictedEnergyUsage'][i];
				
				var newRow = $('<tr>');
				newRow.append($('<td>')); //.append($('<input type="checkbox">')));
				
				var onBox = $('<input type="checkbox" checked="checked">');
				onBox.click(function() { toggleDataSeries('predicted_' + predictedApplianceId); });
				newRow.append($('<td>').append(onBox));
				newRow.append($('<td>').html(i));
				
				newRow.append($('<td></td>'));
				newRow.append($('<td></td>'));
				applianceTable.append(newRow);
			}
		}
		
	};
	
	var zoomOut = function() {
		// find current min and max dates to graph
		
		
		//alert("min: " + new Date(min) + "; max: " + new Date(max));
		
		// find midpoint time
		var mid = 2 * (maxDate - minDate);
		
		var newMin = minDate - mid;
		var newMax = maxDate + mid;
		
		//alert("newmin: " + new Date(newMin) + "; newmax: " + new Date(newMax));
		
		loadSimulationEnergyGraph(newMin,newMax);
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
			
			loadSimulationEnergyGraph(min,max);
			
			return false;
		}
	};
	
	var insertNullsWhereNeeded = function(data) {
       var nullPoints = [];
       var previousTimestamp = data[0];
       $.each(data, function(lineNo, point) {
          if (point[0] - previousTimestamp > 301000) {
             // Gap detected, record where a nullPoint shoud be inserted to stop drawing
             nullPoints.push([lineNo, previousTimestamp]);
          }
          previousTimestamp = point[0];
       });
       // Insert null points before each gap. Must be done in reverse order so null points end up at proper indexes.
       $.each(nullPoints.reverse(), function(lineNo, nullPoint) {
          var nullStamp = [nullPoint[1] + 300000, null];
          data.splice(nullPoint[0], 0, nullStamp);
       });
	};
	
	var constructOnOffTransitionPlotLines = function(onOffTransitionMap, min, max) {
		var lines = [];
		
		//  calc from the current min and max of the points I'm about to show
		var minDistance = new Number(4 * (max - min) / ($('#energyConsumption').width()));
		
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
			            value: time
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
	
	var drawEnergyGraph = function(evaluation) {
		simulatedSeries = [];
		predictedSeries = [];
		
		dataSeriesMap = {};
		var energyusage = {name: 'Total Power Draw', data: evaluation['secondData'], yAxis: 1};
		
		var id = 1;
		
		for(var i in evaluation['simulation']['appliances']) {
			var data = evaluation['simulation']['appliances'][i]['energyTimesteps'];
			insertNullsWhereNeeded(data);
			
			var name = evaluation['simulation']['appliances'][i]['name'];
			simulatedSeries.push({name : name, data: data, yAxis:0});
			dataSeriesMap['simulated_' + i] = id;
			id++;
		}
		
		for(var i in evaluation['predictedEnergyUsage']) {
			var data = evaluation['predictedEnergyUsage'][i];
			insertNullsWhereNeeded(data);
			var name = i;
			var predicted_id = evaluation['predictedEnergyUsageIdMap'][i];
			predictedSeries.push({name : 'predicted ' + name, data: data, yAxis:0});
			dataSeriesMap['predicted_' + predicted_id] = id;
			id++;
		}
		
		var allSeries = [energyusage].concat(simulatedSeries).concat(predictedSeries);
		
		
		var plotLines = constructOnOffTransitionPlotLines(evaluation['predictedApplianceStateTransitions'], evaluation['secondData'][0][0], evaluation['secondData'][evaluation['secondData'].length - 1][0]);
		
		var series;
		
		Highcharts.setOptions({
		    global: {
		        useUTC: false
		    }
		});
			
			var options = {
				chart: {
					renderTo: 'energyConsumption',
					zoomType: 'x',
					events: {
						selection: handleGraphExtremesChange
					}
				},
			    title: {
					text: 'Energy Consumption and Power Draw for 5 minute time boxes'
				},
				xAxis: {
					type: 'datetime',
					maxZoom: 14 * 24 * 3600000, // fourteen days
					title: {
						text: "Timestep (5 minute time box)"
					},
					plotLines: plotLines
				},
				yAxis: [{ 
							title: { text: 'Energy Consumption (Watt-Seconds)' },
							min: 0.6,
							startOnTick: false,
							showFirstLabel: false
						},{
							title: { text: 'Power Draw (Watts)' },
							min: 0.6,
							startOnTick: false,
							showFirstLabel: false
						}],
				legend: {
					enabled: false
				},
				/*plotOptions: {
					series: {
						fillColor: {
							linearGradient: [0, 0, 0, 300],
							stops: [
								[0, '#4572A7'],
								[1, 'rgba(0,0,0,0)']
							]
						},
						lineWidth: 1,
						marker: {
							enabled: false,
							states: {
								hover: {
									enabled: true,
									radius: 3
								}
							}
						},
						shadow: false,
						states: {
							hover: {
								lineWidth: 1						
							}
						}
					}
				},*/
			
				series: allSeries
			};
			
			chart = new Highcharts.Chart(options);
			
			// now add on / off transitions
			
		
	};
	
	
	
	var displaySimulationGroup = function() {
		var simulationGroupId = $('#evaluationGroup').val();
		if(simulationGroupId != undefined && simulationGroupId != '') {
			loadSimulationGroupInfo(simulationGroupId);
		}
	};
	
	var loadSimulationGroupInfo = function(simulationGroupId) {
		
		// load grab energy data from server
		$.getJSON('t/evaluationgroup/' + simulationGroupId)
        .success(function(data) {
        	displaySimulationGroupInfo(data);
        })
        .error(function() {
        	alert('There was a problem getting evaluation group information!');
        });
	};
	
	var displaySimulationGroupInfo = function(data) {
		var accuracyErrorMetrics = data['accuracyErrorMetrics'];
		var stateTransitionAccuracyErrorMetrics = data['stateTransitionAccuracyErrorMetrics'];
		var stateTransitionPrecisionErrorMetrics = data['stateTransitionPrecisionErrorMetrics'];
		var stateTransitionRecallErrorMetrics = data['stateTransitionRecallErrorMetrics'];
		
		energyAccuracyTableBodyEl.empty();
		
		for(var i in accuracyErrorMetrics) {
			var newRow = $('<tr>');
			newRow.append($('<td>').html(i));
			newRow.append($('<td>').html((accuracyErrorMetrics[i][0]).toFixed(1)));
			newRow.append($('<td>').html((accuracyErrorMetrics[i][1]).toFixed(1)));
			newRow.append($('<td>').html((accuracyErrorMetrics[i][2]).toFixed(1)));
			
			// now build cell with specific evaluation numbers
			var individualErrors = data['algorithmEvaluations'][i];
			var individualErrorText = "";
			for(var j in individualErrors) {
				var id = new Number(j) + 1;
				individualErrorText += "<a target='_blank' href=\"t/simulationcontrolpanel/" + individualErrors[j]['simulation']['id'] + "/" + i + "\">" + id + " - " + (individualErrors[j]['overallAccuracy']).toFixed(0) + "</a>; ";
			}
			newRow.append($('<td>').html(individualErrorText));
			
			energyAccuracyTableBodyEl.append(newRow);
		}
		
		
		stateTransitionAccuracyTableBodyEl.empty();
		
		for(var i in stateTransitionAccuracyErrorMetrics) {
			var newRow = $('<tr>');
			newRow.append($('<td>').html(i));
			newRow.append($('<td>').html((stateTransitionAccuracyErrorMetrics[i][0]).toFixed(1)));
			newRow.append($('<td>').html((stateTransitionAccuracyErrorMetrics[i][1]).toFixed(1)));
			newRow.append($('<td>').html((stateTransitionAccuracyErrorMetrics[i][2]).toFixed(1)));
			
			// now build cell with specific evaluation numbers
			var individualErrors = data['algorithmEvaluations'][i];
			var individualErrorText = "";
			for(var j in individualErrors) {
				var id = new Number(j) + 1;
				individualErrorText += "<a target='_blank' href=\"t/simulationcontrolpanel/" + individualErrors[j]['simulation']['id'] + "/" + i + "\">" + id + " - " + (individualErrors[j]['stateTransitionAccuracy']).toFixed(0) + "</a>; ";
			}
			newRow.append($('<td>').html(individualErrorText));
			
			stateTransitionAccuracyTableBodyEl.append(newRow);
		}
		
		
		stateTransitionPrecisionTableBodyEl.empty();
		
		for(var i in stateTransitionPrecisionErrorMetrics) {
			var newRow = $('<tr>');
			newRow.append($('<td>').html(i));
			newRow.append($('<td>').html((stateTransitionPrecisionErrorMetrics[i][0]).toFixed(1)));
			newRow.append($('<td>').html((stateTransitionPrecisionErrorMetrics[i][1]).toFixed(1)));
			newRow.append($('<td>').html((stateTransitionPrecisionErrorMetrics[i][2]).toFixed(1)));
			
			// now build cell with specific evaluation numbers
			var individualErrors = data['algorithmEvaluations'][i];
			var individualErrorText = "";
			for(var j in individualErrors) {
				var id = new Number(j) + 1;
				individualErrorText += "<a target='_blank' href=\"t/simulationcontrolpanel/" + individualErrors[j]['simulation']['id'] + "/" + i + "\">" + id + " - " + (individualErrors[j]['stateTransitionPrecision']).toFixed(0) + "</a>; ";
			}
			newRow.append($('<td>').html(individualErrorText));
			
			stateTransitionPrecisionTableBodyEl.append(newRow);
		}
		
		
		stateTransitionRecallTableBodyEl.empty();
		
		for(var i in stateTransitionRecallErrorMetrics) {
			var newRow = $('<tr>');
			newRow.append($('<td>').html(i));
			newRow.append($('<td>').html((stateTransitionRecallErrorMetrics[i][0]).toFixed(1)));
			newRow.append($('<td>').html((stateTransitionRecallErrorMetrics[i][1]).toFixed(1)));
			newRow.append($('<td>').html((stateTransitionRecallErrorMetrics[i][2]).toFixed(1)));
			
			// now build cell with specific evaluation numbers
			var individualErrors = data['algorithmEvaluations'][i];
			var individualErrorText = "";
			for(var j in individualErrors) {
				var id = new Number(j) + 1;
				individualErrorText += "<a target='_blank' href=\"t/simulationcontrolpanel/" + individualErrors[j]['simulation']['id'] + "/" + i + "\">" + id + " - " + (individualErrors[j]['stateTransitionRecall']).toFixed(0) + "</a>; ";
			}
			newRow.append($('<td>').html(individualErrorText));
			
			stateTransitionRecallTableBodyEl.append(newRow);
		}
	};
	
	var startNewSimulation = function() {
		$.post('t/simulations', {duration: $('#durationInSeconds').val(), onOffLabelsPerAppliance: $('#onOffLabelsPerAppliance').val(), onConcurrency: $('#onConcurrency').val(), numAppliances: $('#numAppliances').val() })
        .error(function() {
        	alert('There was a problem adding the new simulation.');
        });
	};
	
	var startNewSimulationGroup = function() {
		$.post('t/simulationgroups', {numsimulations: $('#numberOfSimulations').val(), duration: $('#durationInSeconds').val(), onOffLabelsPerAppliance: $('#onOffLabelsPerAppliance').val(), onConcurrency: $('#onConcurrency').val(), numAppliances: $('#numAppliances').val() })
        .error(function() {
        	alert('There was a problem adding the new simulation group.');
        });
	};
	
	var init = function() {
		$('#zoomOut').click(zoomOut);
		totalEnergyAccuracy = $('#totalEnergyAccuracy');
		stateTransitionAccuracy = $('#stateTransitionAccuracy');
		stateTransitionRecall = $('#stateTransitionRecall');
		stateTransitionPrecision = $('#stateTransitionPrecision');
		totalCostErrorEl = $('#totalCostError');
		applianceTable = $('#applianceTable');
		energyAccuracyTableBodyEl = $('#energyAccuracyTableBody');
		stateTransitionAccuracyTableBodyEl = $('#stateTransitionAccuracyTableBody');
		stateTransitionPrecisionTableBodyEl = $('#stateTransitionPrecisionTableBody');
		stateTransitionRecallTableBodyEl = $('#stateTransitionRecallTableBody');
		$('#submitNewSimulation').click(startNewSimulation);
		$('#submitNewSimulationGroup').click(startNewSimulationGroup);
		$('#displaySimulation').click(displaySimulation);
		$('#displaySimulationGroup').click(displaySimulationGroup);
		metricsEl = $('#metrics');
		otherStatistics = $('#otherStatistics');
		averageEnergyError = $('#averageEnergyError');
		algorithmEl = $('#algorithm');
		
		if(simulationIdToPreload != undefined && simulationIdToPreload != '' && algorithmNameToPreload != undefined && algorithmNameToPreload != '') {
			$('#evaluation').val(simulationIdToPreload);
			
			$("#algorithm option").filter(function() {
			    return this.text == algorithmNameToPreload; 
			}).attr('selected', true);
			
			displaySimulation();
		}
	};
	
	init();
}

$(document).ready(function() {
	new ControlPanel();
});
