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
package info.raack.appliancedetection.evaluation.data;

import info.raack.appliancedetection.evaluation.model.Simulation;
import info.raack.appliancedetection.evaluation.model.SimulationGroup;
import info.raack.appliancedetection.evaluation.model.appliance.SimulatedAppliance;
import info.raack.appliancelabeler.model.EnergyTimestep;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.SimulatedStateTransition;
import info.raack.appliancelabeler.model.energymonitor.EnergyMonitor;
import info.raack.appliancelabeler.security.OAuthData;
import info.raack.appliancelabeler.service.OAuthRequestProcessor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.oauth.consumer.OAuthSecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

@Component("evalDatabase")
public class JDBCDatabase implements Database {
	


	private SimulationInformationRowMapper simulationInformationRowMapper = new SimulationInformationRowMapper();
	SimulationGroupRowMapper simulationGroupRowMapper = new SimulationGroupRowMapper();

	private Logger logger = LoggerFactory.getLogger(JDBCDatabase.class);

	private JdbcTemplate jdbcTemplate;
	
	@Autowired
    public void init(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }
	
	@Autowired
	private info.raack.appliancelabeler.data.Database engineDatabase;
	
	private String insertForNewSimulationEvaluation = "insert into simulations (id, start_time, duration, num_appliances, on_concurrency, labels_per_appliance, simulation_group_id, done) values (?, ?, ?, ?, ?, ?, ?, ?)";
	private String insertForNewSimulatedAppliance = "insert into simulated_appliances (simulation_id, labeled_appliance_id, class, appliance_num) values (?, ?, ?, ?)";
	private String insertForNewSimulatedApplianceWithoutLabelLink = "insert into simulated_appliances (simulation_id, class, appliance_num) values (?, ?, ?)";
	private String insertForNewSimulatedApplianceEnergyConsumption = "insert into simulated_appliance_energy_consumption_values (simulated_appliance_id, start_time, end_time, energy_consumed) values (?, ?, ?, ?)";
	private String insertForNewSimulatedApplianceStateTransitions = "insert into simulated_appliance_state_transitions (simulated_appliance_id, time, start_on) values (?, ?, ?)";
	private String updateSimulationToDone = "update simulations set done = true where id = ?";
	
	private String queryForAllSimulationInformation = "select * from simulations order by start_time";
	private String queryForSimulationInformationByGroup = "select * from simulations where simulation_group_id = ?";
	private String queryForSimulationById = "select * from simulations where id = ?";
	private String queryForSimulatedAppliancesBySimulationId = "select * from simulated_appliances where simulation_id = ? order by appliance_num";
	private String queryForSimulatedEnergyTimestepsBySimulatedApplianceIdAndDates = "select * from simulated_appliance_energy_consumption_values where simulated_appliance_id = ? and start_time >= ? and end_time <= ? order by start_time";
	private String queryForSimulatedStateTransitionsBySimulatedApplianceIdAndDates = "select * from simulated_appliance_state_transitions where simulated_appliance_id = ? and time >= ? and time <= ? order by time";
	private String queryForSimulationGroupById = "select sg.*, count(s1.id) as simulation_count, count(s1.id) = count(s2.id) as done from simulation_groups sg, simulations s1, simulations s2 where sg.id = ? and sg.id = s1.simulation_group_id and sg.id = s2.simulation_group_id and s2.done = true and s1.id = s2.id group by sg.id";
	private String queryForAllSimulationGroupsInfo = "select sg.*, count(s1.id) as simulation_count, count(s1.id) = count(s2.id) as done from simulation_groups sg, simulations s1, simulations s2 where sg.id = s1.simulation_group_id and sg.id = s2.simulation_group_id and s2.done = true and s1.id = s2.id group by sg.id";
	
	@Transactional
	public void saveSimulation(final Simulation simulation) {
		// save simulation / evaluation
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(insertForNewSimulationEvaluation, new Object[] {simulation.getId(), simulation.getStartTime().getTime(), simulation.getDurationInSeconds(), simulation.getNumAppliances(), simulation.getOnConcurrency(), simulation.getLabelsPerOnOff(), simulation.getGroup() != null ? simulation.getGroup().getId() : null, false });
		
		// save simulated appliances
		for(final SimulatedAppliance appliance : simulation.getSimulatedAppliances()) {
			keyHolder = new GeneratedKeyHolder();
			
			if(appliance.getLabeledAppliance() != null) {
				jdbcTemplate.update(
				    new PreparedStatementCreator() {
				        public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
				            PreparedStatement ps = connection.prepareStatement(insertForNewSimulatedAppliance, new String[] {"id"});
				            ps.setString(1, simulation.getId());
				            ps.setInt(2, appliance.getLabeledAppliance().getId());
				            ps.setString(3, appliance.getClass().getName());
				            ps.setInt(4, appliance.getApplianceNum());
				            return ps;
				        }
				    },
				    keyHolder);
			} else {
				jdbcTemplate.update(
					    new PreparedStatementCreator() {
					        public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
					            PreparedStatement ps = connection.prepareStatement(insertForNewSimulatedApplianceWithoutLabelLink, new String[] {"id"});
					            ps.setString(1, simulation.getId());
					            ps.setString(2, appliance.getClass().getName());
					            ps.setInt(3, appliance.getApplianceNum());
					            return ps;
					        }
					    },
					    keyHolder);
			}
			
				
			final int simulatedApplianceDatabaseId = keyHolder.getKey().intValue();
			
			// save simulated appliance energy consumption
			jdbcTemplate.batchUpdate(insertForNewSimulatedApplianceEnergyConsumption, new BatchPreparedStatementSetter() {
				
				public void setValues(PreparedStatement ps, int i) throws SQLException {
					EnergyTimestep energyTimestep = appliance.getEnergyTimesteps().get(i);
					
					ps.setInt(1, simulatedApplianceDatabaseId);
		            ps.setLong(2, energyTimestep.getStartTime().getTime());
		            ps.setLong(3, energyTimestep.getEndTime().getTime());
		            ps.setInt(4, (int)energyTimestep.getEnergyConsumed());
				}
				
				public int getBatchSize() {
					return appliance.getEnergyTimesteps().size();
				}
			});
			
			// save simulated appliance state transitions
			jdbcTemplate.batchUpdate(insertForNewSimulatedApplianceStateTransitions, new BatchPreparedStatementSetter() {
				
				public void setValues(PreparedStatement ps, int i) throws SQLException {
					ApplianceStateTransition stateTransition = appliance.getAllApplianceStateTransitions().get(i);
					
					ps.setInt(1, simulatedApplianceDatabaseId);
		            ps.setLong(2, stateTransition.getTime());
		            ps.setBoolean(3, stateTransition.isOn());
				}
				
				public int getBatchSize() {
					return appliance.getAllApplianceStateTransitions().size();
				}
			});
		}
	}

	
	
	public List<Simulation> getAllSimulationInformation() {
		return jdbcTemplate.query(queryForAllSimulationInformation , simulationInformationRowMapper);
	}

	public Simulation getSimulation(String simulationId, final Date start, final Date end, final boolean includeRawPowerMeasurements) {
		// save simulation
		return jdbcTemplate.queryForObject(queryForSimulationById, new Object[] {simulationId}, new RowMapper<Simulation>() {

			public Simulation mapRow(ResultSet rs, int arg1) throws SQLException {
				final String simulationId = rs.getString("id");
				Date startTime = new Date(rs.getLong("start_time"));
				int resultDuration = rs.getInt("duration");
				int numAppliances = rs.getInt("num_appliances");
				int onConcurrency = rs.getInt("on_concurrency");
				int labelsPerAppliance = rs.getInt("labels_per_appliance");
				
				Calendar time = new GregorianCalendar();
				time.setTime(startTime);
				time.add(Calendar.SECOND, resultDuration);
				Date endTime = time.getTime();
				
				EnergyMonitor monitor = getSimulatedEnergyMonitor(simulationId);
				
				final Date queryStartDate = start == null ? startTime : start;
				final Date queryEndDate = end == null ? endTime : end;
				
				int duration = (int)((queryEndDate.getTime() - queryStartDate.getTime()) / 1000);
					
				List<SecondData> simulatedPowerDraw = new ArrayList<SecondData>();
				
				if(includeRawPowerMeasurements) {
					simulatedPowerDraw = engineDatabase.getEnergyMeasurementsForMonitor(monitor, queryStartDate, queryEndDate, 200);
				}
				
				// create empty list of simulated appliances first, so that the simulation is already constructed when it is needed by the appliance constructor
				List<SimulatedAppliance> appliances = new ArrayList<SimulatedAppliance>();
				
				final Simulation simulation = new Simulation(simulationId, queryStartDate, duration, numAppliances, onConcurrency, labelsPerAppliance, appliances, simulatedPowerDraw);
				
				List<SimulatedAppliance> newAppliances = jdbcTemplate.query(queryForSimulatedAppliancesBySimulationId, new Object[] {simulationId}, new RowMapper<SimulatedAppliance>() {

					public SimulatedAppliance mapRow(ResultSet rs, int arg1) throws SQLException {
						final int simulatedApplianceId = rs.getInt("id");
						int applianceNum = rs.getInt("appliance_num");
						String className = rs.getString("class");
						
						try {
							// create the specific class called for
							Class<? extends SimulatedAppliance> clazz = (Class<? extends SimulatedAppliance>)Class.forName(className);
							final SimulatedAppliance simulatedAppliance = clazz.newInstance().initialize(simulation, applianceNum, true);
	
							int labeledApplianceId = rs.getInt("labeled_appliance_id");
							if(labeledApplianceId > 0) {
								UserAppliance labeledAppliance = engineDatabase.getUserApplianceById(labeledApplianceId);
								simulatedAppliance.setLabeledAppliance(labeledAppliance);
							}
							
							// get energy timesteps
							List<EnergyTimestep> energyTimesteps = jdbcTemplate.query(queryForSimulatedEnergyTimestepsBySimulatedApplianceIdAndDates, new Object[] {simulatedApplianceId, queryStartDate.getTime(), queryEndDate.getTime()}, new RowMapper<EnergyTimestep>() {
	
								public EnergyTimestep mapRow(ResultSet rs, int arg1) throws SQLException {
									EnergyTimestep energyTimestep = new EnergyTimestep();
									
									energyTimestep.setStartTime(new Date(rs.getLong("start_time")));
									energyTimestep.setEndTime(new Date(rs.getLong("end_time")));
									energyTimestep.setEnergyConsumed(rs.getFloat("energy_consumed"));
									
									return energyTimestep;
								}});
							
							simulatedAppliance.setEnergyTimesteps(energyTimesteps);
							
							
							// get state transitions
							List<ApplianceStateTransition> stateTransitions = jdbcTemplate.query(queryForSimulatedStateTransitionsBySimulatedApplianceIdAndDates, new Object[] {simulatedApplianceId, queryStartDate.getTime(), queryEndDate.getTime()}, new RowMapper<ApplianceStateTransition>() {
	
								public ApplianceStateTransition mapRow(ResultSet rs, int arg1) throws SQLException {
									SimulatedStateTransition transition = new SimulatedStateTransition(rs.getInt("id"), simulatedAppliance, 0, rs.getBoolean("start_on"), rs.getLong("time"));
									
									return transition;
								}});
							
							simulatedAppliance.setAllStateTransitions(stateTransitions);
							
							return simulatedAppliance;
						} catch (Exception e) {
							throw new RuntimeException("Could not create simulated appliance class", e);
						}
					}});
				
				// now add the actual appliances to the correct appliances list
				appliances.addAll(newAppliances);
				
				return simulation;
			}
		});
	}

	@Override
	public boolean isSimulationDone(String simulationId) {
		try {
			return jdbcTemplate.queryForObject("select done from simulations where id = ?", new Object[] {simulationId}, Boolean.class);
		} catch (EmptyResultDataAccessException e) {
			return false;
		}
	}

	@Override
	public void saveSimulationGroup(final SimulationGroup group) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		
		jdbcTemplate.update(
		    new PreparedStatementCreator() {
		        public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
		            PreparedStatement ps = connection.prepareStatement("insert into simulation_groups (start_time, duration, num_appliances, on_concurrency, labels_per_appliance) values (?, ?, ?, ?, ?)", new String[] {"id"});
		            ps.setLong(1, group.getStartTime().getTime());
		            ps.setInt(2, group.getDurationInSeconds());
		            ps.setInt(3, group.getNumAppliances());
		            ps.setInt(4, group.getOnConcurrency());
		            ps.setInt(5, group.getLabelsPerOnOff());
		            
		            return ps;
		        }
		    },
		    keyHolder);
		
		group.setId(keyHolder.getKey().intValue());
	}

	@Override
	public void setDone(Simulation simulation) {
		jdbcTemplate.update(updateSimulationToDone, simulation.getId());
	}

	@Override
	public List<SimulationGroup> getAllSimulationGroupInfo() {
		return jdbcTemplate.query(queryForAllSimulationGroupsInfo, simulationGroupRowMapper);
	}

	@Override
	public List<Simulation> getAllSimulationInformationForGroup(int simulationGroupId) {
		return jdbcTemplate.query(queryForSimulationInformationByGroup, new Object[] {simulationGroupId}, simulationInformationRowMapper);
	}

	@Override
	public SimulationGroup getSimulationGroup(int simulationGroupId) {
		return jdbcTemplate.queryForObject(queryForSimulationGroupById, new Object[] {simulationGroupId}, simulationGroupRowMapper);
	}
	
	@Override
	public EnergyMonitor getSimulatedEnergyMonitor(final String simulationId) {
		int monitorId = jdbcTemplate.queryForInt("select id from energy_monitors where monitor_type = 'simulator' and user_id = 'simulator' and monitor_id = ?", new Object[] {simulationId});
		
		EnergyMonitor monitor = new EnergyMonitor(monitorId, "simulator") {
			public Date getCurrentMonitorTime(OAuthData oAuthData) { return null; }
			public String getMonitorId() { return simulationId; }
			public List<SecondData> getSecondData(OAuthRequestProcessor oAuthRequestProcessor, OAuthSecurityContext oAuthContext, long offset, long datapointsBack) { return null; }
			public String getType() { return "simulator"; }};
		return monitor;
	}


	private final class SimulationGroupRowMapper implements RowMapper<SimulationGroup> {
		@Override
		public SimulationGroup mapRow(ResultSet rs, int arg1) throws SQLException {
			int id = rs.getInt("id");
			Date startTime = new Date(rs.getLong("start_time"));
			int duration = rs.getInt("duration");
			int numAppliances = rs.getInt("num_appliances");
			int onConcurrency = rs.getInt("on_concurrency");
			int labelsPerAppliance = rs.getInt("labels_per_appliance");
			boolean done = rs.getBoolean("done");
			int simulationCount = rs.getInt("simulation_count");
			
			SimulationGroup simulationGroup = new SimulationGroup(simulationCount,startTime, duration, numAppliances, onConcurrency, labelsPerAppliance);
			simulationGroup.setId(id);
			simulationGroup.setDone(done);
		
			return simulationGroup;
		}
	}

	private class SimulationInformationRowMapper implements RowMapper<Simulation> {
		public Simulation mapRow(ResultSet rs, int arg1) throws SQLException {
			String simulationId = rs.getString("id");
			Date startTime = new Date(rs.getLong("start_time"));
			int duration = rs.getInt("duration");
			int numAppliances = rs.getInt("num_appliances");
			int onConcurrency = rs.getInt("on_concurrency");
			int labelsPerAppliance = rs.getInt("labels_per_appliance");
			
			return new Simulation(simulationId, startTime, duration, numAppliances, onConcurrency, labelsPerAppliance, Collections.<SimulatedAppliance>emptyList(), Collections.<SecondData>emptyList());
		}
	}
}
