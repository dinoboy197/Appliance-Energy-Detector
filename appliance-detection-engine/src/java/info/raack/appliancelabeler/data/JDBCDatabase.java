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
package info.raack.appliancelabeler.data;

import info.raack.appliancelabeler.data.batch.ItemReader;
import info.raack.appliancelabeler.data.batch.SpringItemReaderAdapter;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.AlgorithmResult;
import info.raack.appliancelabeler.machinelearning.appliancedetection.algorithms.ApplianceEnergyConsumptionDetectionAlgorithm;
import info.raack.appliancelabeler.model.AlgorithmPredictions;
import info.raack.appliancelabeler.model.Appliance;
import info.raack.appliancelabeler.model.EnergyTimestep;
import info.raack.appliancelabeler.model.UserAppliance;
import info.raack.appliancelabeler.model.appliancestatetransition.ApplianceStateTransition;
import info.raack.appliancelabeler.model.appliancestatetransition.GenericStateTransition;
import info.raack.appliancelabeler.model.energymonitor.EnergyMonitor;
import info.raack.appliancelabeler.model.energymonitor.Ted5000Monitor;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;

import javax.sql.DataSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.resource.ListPreparedStatementSetter;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.jdbc.core.support.SqlLobValue;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.oauth.consumer.token.OAuthConsumerToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;

@Component
@Transactional
public class JDBCDatabase implements Database {
	private Logger logger = LoggerFactory.getLogger(JDBCDatabase.class);

	private JdbcTemplate jdbcTemplate;
	
	private DataSource dataSource;
	
	@Value("${stepgreen.basehost}")
	private String stepgreenBasehost;
	
    private String insertForNewUserAppliance = "insert into user_appliances (energy_monitor_id, appliance_id, name, algorithm_generated) values (?, ?, ?, ?)";
    private String insertForNewUserApplianceForAlgorithm = "insert into user_appliances (energy_monitor_id, appliance_id, name, algorithm_id, algorithm_generated) values (?, ?, ?, ?, ?)";
    
	private String queryForExistingEnergyMonitors = "select * from energy_monitors";
	private String queryForExistingEnergyMonitorId = "select id from energy_monitors where user_id = ? and monitor_type = ? and monitor_id = ?";
	private String insertForNewEnergyMonitor = "insert into energy_monitors (user_id, monitor_type, monitor_id, last_measurement_offset) values (?, ?, ?, ?)";
	private String insertNewEnergyReadings = "insert into energy_measurements (energy_monitor_id, reading_time, power, voltage) values (?, ?, ?, ?) on duplicate key update reading_time=values(reading_time), power=values(power), voltage=values(voltage)";
	private String queryForLatestEnergyReadingOffset = "select last_measurement_offset from energy_monitors m where m.user_id = ? and m.monitor_id = ?";
	private String saveAccessTokensForUser = "insert into user_oauth_tokens (user_id, user_email, spring_oauth_serialized_token_map) values (?, ?, ?) on duplicate key update user_email=values(user_email), spring_oauth_serialized_token_map=values(spring_oauth_serialized_token_map)";
	private String saveAccessTokensForUserWithoutEmail = "insert into user_oauth_tokens (user_id, user_email, spring_oauth_serialized_token_map) values (?, 'test', ?) on duplicate key update spring_oauth_serialized_token_map=values(spring_oauth_serialized_token_map)";
	private String getAccessTokensForUser = "select spring_oauth_serialized_token_map from user_oauth_tokens where user_id = ?";
	private String updateLastOffset = "update energy_monitors set last_measurement_offset = ? where user_id = ? and monitor_id = ?";
	private String queryForAllUserOAuthTokens = "select * from user_oauth_tokens";
	private String queryForTedFeedMeasurementsOnSpecificSeconds = "select e.* from energy_measurements e where e.energy_monitor_id = :id and e.reading_time in (:timestamps) order by e.reading_time";
	private String queryForTedFeedMeasurementsWithTimeBoundaries = "select e.* from energy_measurements e where e.energy_monitor_id = :id and e.reading_time >= :min and e.reading_time <= :max order by e.reading_time";
	private String queryForDistinctDetectedStateTransitionAlgorithmIds = "select distinct st.detection_algorithm from appliance_state_transitions st, user_appliances ua, energy_monitors em where em.user_id = ? and em.monitor_type = ? and em.monitor_id = ? and em.id = ua.energy_monitor_id and ua.id = st.user_appliance_id";
	private String queryForDistinctDetectedEnergyConsumptionAlgorithmIds = "select distinct st.detection_algorithm from appliance_energy_consumption_predictions st, user_appliances ua, energy_monitors em where em.user_id = ? and em.monitor_type = ? and em.monitor_id = ? and em.id = ua.energy_monitor_id and ua.id = st.user_appliance_id";
	private String insertApplianceStateTransition = "insert into appliance_state_transitions (user_appliance_id, detection_algorithm, time, start_on) values (?, ?, ?, ?)";
	private String queryForLastMeasurementTime = "select max(m.reading_time) as max from energy_measurements m, energy_monitors em where em.user_id = ? and em.monitor_type = ? and em.monitor_id = ? and em.id = m.energy_monitor_id";
	private String queryForAllUserIds = "select user_id from user_oauth_tokens";

	private String insertEnergyTimesteps = "insert into appliance_energy_consumption_predictions (detection_algorithm, user_appliance_id, start_time, end_time, energy_consumed) values (?, ?, ?, ?, ?)";
	private String queryForUserAppliancesByAlgorithmId = "select a.id as appliance_id, a.description as appliance_description, ua.id as user_appliance_id, ua.name as user_appliance_name, ua.algorithm_id, ua.algorithm_generated from user_appliances ua, appliances a, energy_monitors m where m.user_id = ? and m.monitor_type = ? and m.monitor_id = ? and m.id = ua.energy_monitor_id and ua.appliance_id = a.id and (ua.algorithm_id = ? or ua.algorithm_id is null)";
	private String queryForUserGeneratedUserAppliances = "select a.id as appliance_id, a.description as appliance_description, ua.id as user_appliance_id, ua.name as user_appliance_name, ua.algorithm_id, ua.algorithm_generated from user_appliances ua, appliances a, energy_monitors m where m.user_id = ? and m.monitor_type = ? and m.monitor_id = ? and m.id = ua.energy_monitor_id and ua.appliance_id = a.id and ua.algorithm_id is null";
	private String queryForUserApplianceById = "select a.id as appliance_id, a.description as appliance_description, ua.id as user_appliance_id, ua.name as user_appliance_name, ua.algorithm_id, ua.algorithm_generated from user_appliances ua, appliances a where ua.id = ? and ua.appliance_id = a.id";
	private String queryForApplianceEnergyConsumptionAverages = "select appliance_id, avg(energy_consumed) as average_energy_consumed from (select a.id as appliance_id, ua.id as user_appliance_id, sum(aecp.energy_consumed) as energy_consumed from appliances a, user_appliances ua, energy_monitors em, appliance_energy_consumption_predictions aecp where a.id = ua.appliance_id and ua.energy_monitor_id = em.id and em.monitor_type != 'simulator' and ua.id = aecp.user_appliance_id and aecp.detection_algorithm = ? and aecp.start_time >= ? and em.id != ? group by a.id, ua.id) as derived group by appliance_id";
	
	private ApplianceRowMapper applianceRowMapper = new ApplianceRowMapper();
	private UserApplianceMapper userApplianceMapper = new UserApplianceMapper();
	private EnergyMonitorRowMapper energyMonitorRowMapper = new EnergyMonitorRowMapper();
	
	@Autowired
    public void init(DataSource dataSource) {
		this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }
	
	public void storeData(final EnergyMonitor energyMonitor, final List<SecondData> data, final Long lastOffset) {
		// check to see if we need a new row in energy_monitor
		final String userId = energyMonitor.getUserId();
		
		final String monitorId = energyMonitor.getMonitorId();
		final String monitorType = energyMonitor.getType();
		
		int tempEnergyMonitorId = getIdForEnergyMonitor(energyMonitor);
		
		final int energyMonitorId = tempEnergyMonitorId;
		
		// save the batch
		long timestart = System.currentTimeMillis();
		
		// don't insert more than a million rows at a time
		
		boolean lastLoop = false;
		for(int j = 0; lastLoop != true; j++) {
			final List<SecondData> tempData = new ArrayList<SecondData>();
			
			final int MAX_INSERTS = 2000000;
			
			int start = j*MAX_INSERTS;
			
			if(data.size() < (j+1)*MAX_INSERTS) {
				// remaining data is less than 1 million rows
				int end = data.size();
				logger.debug("Inserting records from " + start + " to " + end);
				tempData.addAll(data.subList(start, end));
				lastLoop = true;
			} else {
				int end = (j+1)*MAX_INSERTS;
				logger.debug("Inserting records from " + start + " to " + end);
				tempData.addAll(data.subList(start,end));
			}
			
			logger.debug("Inserting " + tempData.size() + " energy measurements for " + userId);
			jdbcTemplate.batchUpdate(insertNewEnergyReadings, new BatchPreparedStatementSetter() {
	            public void setValues(PreparedStatement ps, int i) throws SQLException {

	            	SecondData secondData = tempData.get(i);
	            	
	            	if(i % 10000 == 0) {
	            		logger.debug("Preparing for record " + i);
	            	}
	            	
	                ps.setInt(1, energyMonitorId);
	                ps.setLong(2, secondData.getCalLong());
	                ps.setInt(3, secondData.getPower());
	                ps.setFloat(4, secondData.getVoltage());
	            }
	
	            public int getBatchSize() {
	                return tempData.size();
	            }
	        });
		}
		long timestop = System.currentTimeMillis();
		float total = (timestop - timestart) / 1000;
		logger.debug("Inserting " + data.size() + " rows into database took " + total + " seconds; " + (data.size() / total) + " rows per second");
		
		jdbcTemplate.update(updateLastOffset, new Object[] {lastOffset, userId, monitorId});
	}

	private int getIdForEnergyMonitor(final EnergyMonitor energyMonitor) {
		if(energyMonitor.getId() > 0) {
			return energyMonitor.getId();
		} else {
			final String userId = energyMonitor.getUserId();
			final String monitorId = energyMonitor.getMonitorId();
			final String monitorType = energyMonitor.getType();
			
			try {
				return jdbcTemplate.queryForInt(queryForExistingEnergyMonitorId, new Object[] { userId, monitorType, monitorId });
			} catch (IncorrectResultSizeDataAccessException e) {
				// there was no row for this energy monitor - insert a row for it
				logger.debug("No energy monitor row for this monitor (user " + userId + ", monitor " + monitorId + ")");
				
				KeyHolder keyHolder = new GeneratedKeyHolder();
				jdbcTemplate.update(
				    new PreparedStatementCreator() {
				        public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
				            PreparedStatement ps = connection.prepareStatement(insertForNewEnergyMonitor, new String[] {"id"});
				            ps.setString(1, userId);
				            ps.setString(2, monitorType);
				            ps.setString(3, monitorId);
				            ps.setLong(4, -1);
				            return ps;
				        }
				    },
				    keyHolder);
				
				
				int newId = keyHolder.getKey().intValue();
				energyMonitor.setId(newId);
				return newId;
			}
		}
	}

	public Long getLastDatapoint(EnergyMonitor energyMonitor) {
		try {
			Long offset = jdbcTemplate.queryForLong(queryForLatestEnergyReadingOffset, new Object[] {energyMonitor.getUserId(), energyMonitor.getMonitorId()});
			return offset;
		} catch (Exception e) {
			logger.info("Could not get last datapoint offset; returning -1");
			return -1L;
		}
	}

	@Override
	public Map<String, OAuthConsumerToken> getOAuthTokensForUserId(String userId) {
		
		Map<String,OAuthConsumerToken> tokens = new HashMap<String,OAuthConsumerToken>();
		
		try {
			Blob blob = jdbcTemplate.queryForObject(getAccessTokensForUser, new Object[] {userId}, Blob.class);

			try {
				Object tokenObject = SerializationUtils.deserialize(blob.getBinaryStream());
				tokens = (Map<String,OAuthConsumerToken>)tokenObject;
			} catch (Exception e) {
				throw new RuntimeException("Could not deserialize tokens for user id " + userId, e);
			}
		} catch (EmptyResultDataAccessException e) {
			logger.debug("No oauth tokens for " + userId);
		}
		
		return tokens;
	}
	

	@Override
	public String getUserEmailForUserId(String userId) {
		String email = null;
		
		try {
			email = jdbcTemplate.queryForObject("select user_email from user_oauth_tokens where user_id = ?", new Object[] {userId}, String.class);
		} catch (EmptyResultDataAccessException e) {
			logger.debug("No email for " + userId);
		}
		return email;
	}

	public void saveOAuthTokensForUserId(String userId, String email, Map<String, OAuthConsumerToken> tokens) {
		HashMap<String,OAuthConsumerToken> hashMapForTokens = new HashMap<String,OAuthConsumerToken>();
		hashMapForTokens.putAll(tokens);
		
		SqlLobValue blob = new SqlLobValue(SerializationUtils.serialize(hashMapForTokens));
		
		if(email != null) {
			jdbcTemplate.update(saveAccessTokensForUser, new Object[] {userId, email, blob}, new int[] {Types.VARCHAR, Types.VARCHAR, Types.BLOB});
		} else {
			jdbcTemplate.update(saveAccessTokensForUserWithoutEmail, new Object[] {userId, blob}, new int[] {Types.VARCHAR, Types.BLOB});
		}
	}
	
	private class EnergyMonitorRowMapper implements RowMapper<EnergyMonitor> {
		public EnergyMonitor mapRow(ResultSet rs, int arg1) throws SQLException {
			int id = rs.getInt("id");
			String userId = rs.getString("user_id");
			String monitorType = rs.getString("monitor_type");
			String monitorId = rs.getString("monitor_id");
			
			if(monitorType.equals("ted5000")) {
				StringTokenizer tokenizer = new StringTokenizer(monitorId, "_");
				String tedId = tokenizer.nextToken();
				String mtuId = tokenizer.nextToken();
				
				return new Ted5000Monitor(id, userId, tedId, mtuId, stepgreenBasehost);
			}
			// TODO - redesign this code so that it doesn't have to know about the simulator, but also doesn't throw an exception when it doesn't know about the type
			else if(monitorType.equals("simulator")) {
				return null;
			} else {
				throw new IllegalArgumentException("Don't know about monitor type " + monitorType);
			}
		}
	}

	private class EnergyMeasurementQueryItems {
		public String sql;
		public MapSqlParameterSource parameters;
	}

	

	private EnergyMeasurementQueryItems getEnergyMeasurementQueryItems(EnergyMonitor energyMonitor, Date start, Date end, int ticks) {
		List<Long> dates = new ArrayList<Long>();
		long totalSeconds = (end.getTime() - start.getTime()) / 1000;
		
		float spaceBetweenTicks = totalSeconds / ticks;
		
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("id", energyMonitor.getId());
		
		String sql = "";
		
		if(spaceBetweenTicks > 1.0f) {
			// create explicitly dates on which to query. This would seem to be the most efficient way to query, since I have a composite index on energy_monitor_id and reading_time, rather than using rownums and the % operator (which would need to create extra
			logger.debug("Running query for specific dates");

			float currentIncrement = 0f;
			
			Calendar c = new GregorianCalendar();
			c.setTime(end);
			
			for(int i = 0; i < ticks; i++) {
				dates.add(c.getTimeInMillis());
				
				// increment by seconds, roughly
				currentIncrement -= spaceBetweenTicks;
				c.setTime(end);
				c.add(Calendar.SECOND, (int)currentIncrement);
			}
			
			Collections.reverse(dates);
			
			logger.debug("First date: " + dates.get(0));
			logger.debug("Last date: " + dates.get(dates.size() - 1));
			parameters.addValue("timestamps", dates);
			
			sql = queryForTedFeedMeasurementsOnSpecificSeconds;
		} else {
			// we want to get every tick, since we can't split a second
			logger.debug("Running query for all dates");
			parameters.addValue("min", start.getTime());
			parameters.addValue("max", end.getTime());
			sql = queryForTedFeedMeasurementsWithTimeBoundaries;
		}
		
		EnergyMeasurementQueryItems items = new EnergyMeasurementQueryItems();
		items.sql = sql;
		items.parameters = parameters;
		return items;
	}
	
	public List<SecondData> getEnergyMeasurementsForMonitor(EnergyMonitor energyMonitor, Date start, Date end, int ticks) {
		EnergyMeasurementQueryItems items = getEnergyMeasurementQueryItems(energyMonitor, start, end, ticks);

		NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(jdbcTemplate);
		
		return template.query(items.sql, items.parameters, secondDataRowMapper);
	}
	
	@Override
	public ItemReader<SecondData> getEnergyMeasurementReaderForMonitor(EnergyMonitor energyMonitor, Date start, Date end, int ticks) {
		EnergyMeasurementQueryItems items = getEnergyMeasurementQueryItems(energyMonitor, start, end, ticks);
		
		JdbcCursorItemReader<SecondData> dataReader = new JdbcCursorItemReader<SecondData>();
		dataReader.setDataSource(dataSource);
		dataReader.setRowMapper(secondDataRowMapper);
		
		// crucial if using mysql to ensure that results are streamed
		dataReader.setFetchSize(Integer.MIN_VALUE);
		dataReader.setVerifyCursorPosition(false);
		
		ParsedSql parsedSql = NamedParameterUtils.parseSqlStatement(items.sql);
		String sql = NamedParameterUtils.substituteNamedParameters(parsedSql, items.parameters);
		ListPreparedStatementSetter listPreparedStatementSetter = new ListPreparedStatementSetter();
		listPreparedStatementSetter.setParameters(Arrays.asList(NamedParameterUtils.buildValueArray(parsedSql, items.parameters, null)));
		dataReader.setPreparedStatementSetter(listPreparedStatementSetter);
		dataReader.setSql(sql);
		
		ExecutionContext executionContext = new ExecutionContext();
		dataReader.open(executionContext);
		
		return new SpringItemReaderAdapter<SecondData>(dataReader);
	}
	
	private class ApplianceStateTransitionMapper implements RowMapper<ApplianceStateTransition> {
		private final ApplianceEnergyConsumptionDetectionAlgorithm algorithm;

		private ApplianceStateTransitionMapper(ApplianceEnergyConsumptionDetectionAlgorithm algorithm) {
			this.algorithm = algorithm;
		}

		@Override
		public ApplianceStateTransition mapRow(ResultSet rs, int arg1) throws SQLException {
			
			// not including ua appliance id or algorithm id
			UserAppliance userAppliance = new UserAppliance(rs.getInt("ua_id"), null, rs.getString("ua_name"), -1, rs.getBoolean("ua_algorithm_generated"));
			return new GenericStateTransition(rs.getInt("ast_id"), userAppliance, algorithm != null ? algorithm.getId() : -1, rs.getBoolean("ast_start_on"), rs.getLong("ast_time"));
		}
	}

	private class UserApplianceMapper implements RowMapper<UserAppliance> {
		public UserAppliance mapRow(ResultSet rs, int arg1) throws SQLException {
			Appliance appliance = new Appliance();
			appliance.setId(rs.getInt("appliance_id"));
			appliance.setDescription(rs.getString("appliance_description"));
			
			int algorithmId = rs.getInt("algorithm_id");
			int userApplianceId = rs.getInt("user_appliance_id");
			String userApplianceName = rs.getString("user_appliance_name");
			boolean algorithmGenerated = rs.getBoolean("algorithm_generated");
			
			UserAppliance app = new UserAppliance(userApplianceId, appliance, userApplianceName, algorithmId, algorithmGenerated);
			
			return app;
		}
	}

	private class UserOAuthTokenCarrier {
		public String userId;
		public Map<String,OAuthConsumerToken> tokens;
	}
	
	public Map<String, Map<String, OAuthConsumerToken>> getOAuthTokensForAllUsers() {
		try {
			List<UserOAuthTokenCarrier> carriers = jdbcTemplate.query(queryForAllUserOAuthTokens, new RowMapper<UserOAuthTokenCarrier>() {

				public UserOAuthTokenCarrier mapRow(ResultSet rs, int arg1) throws SQLException {
					UserOAuthTokenCarrier token = new UserOAuthTokenCarrier();
					token.userId = rs.getString("user_id");
					Blob blob = rs.getBlob("spring_oauth_serialized_token_map");
					Object tokenObject = SerializationUtils.deserialize(blob.getBinaryStream());
					Map<String,OAuthConsumerToken> tokens = (Map<String,OAuthConsumerToken>)tokenObject;
					token.tokens = tokens;
					
					return token;
				}} );
			
			Map<String, Map<String, OAuthConsumerToken>> tokens = new HashMap<String, Map<String, OAuthConsumerToken>>();
			for(UserOAuthTokenCarrier carrier : carriers) {
				tokens.put(carrier.userId, carrier.tokens);
			}
			
			return tokens;
		} catch (Exception e) {
			throw new RuntimeException("Error while getting user tokens", e);
		}
	}

	public List<Integer> getAlgorithmIdsForCurrentStateTranstionsForMonitor(EnergyMonitor energyMonitor) {
		return jdbcTemplate.queryForList(queryForDistinctDetectedStateTransitionAlgorithmIds, new Object[] {energyMonitor.getUserId(), energyMonitor.getType(), energyMonitor.getMonitorId()}, Integer.class);
	}
	
	public List<Integer> getAlgorithmIdsForCurrentEnergyConsumptionForMonitor(EnergyMonitor energyMonitor) {
		return jdbcTemplate.queryForList(queryForDistinctDetectedEnergyConsumptionAlgorithmIds, new Object[] {energyMonitor.getUserId(), energyMonitor.getType(), energyMonitor.getMonitorId()}, Integer.class);
	}

	public List<SecondData> getEnergyMeasurementsForMonitor(EnergyMonitor energyMonitor, long startingMeasurement, long numberOfMeasurements) {
		return jdbcTemplate.query("select m.* from energy_measurements m, energy_monitors em where em.user_id = ? and em.monitor_type = ? and em.monitor_id = ? and em.id = m.energy_monitor_id order by m.reading_time limit ?,?", new Object[] {energyMonitor.getUserId(), energyMonitor.getType(), energyMonitor.getMonitorId(), startingMeasurement, numberOfMeasurements}, secondDataRowMapper);
	}

	public List<EnergyMonitor> getEnergyMonitors() {
		List<EnergyMonitor> monitors = jdbcTemplate.query(queryForExistingEnergyMonitors, energyMonitorRowMapper);
		
		monitors.removeAll(Collections.singletonList(null));
		
		return monitors;
	}

	@Transactional
	public void storeAlgorithmPredictions(EnergyMonitor energyMonitor, final Map<Integer, AlgorithmPredictions> allAlgorithmPredictions) {
		for(final Integer algorithmId : allAlgorithmPredictions.keySet()) {
			final List<ApplianceStateTransition> detectedStateTransitions = allAlgorithmPredictions.get(algorithmId).getStateTransitions();
			
			for(ApplianceStateTransition transition : detectedStateTransitions) {
				if(transition.getUserAppliance().getId() == -1) {
					// save the user appliance
					addUserAppliance(energyMonitor, transition.getUserAppliance());
				}
			}
			storeUserOnOffLabels(detectedStateTransitions);
		}
		
		for(final Integer algorithmId : allAlgorithmPredictions.keySet()) {
			for(final UserAppliance userAppliance : allAlgorithmPredictions.get(algorithmId).getEnergyTimesteps().keySet()) {
				final List<EnergyTimestep> energyTimesteps = allAlgorithmPredictions.get(algorithmId).getEnergyTimesteps().get(userAppliance);
				
				jdbcTemplate.batchUpdate(insertEnergyTimesteps , new BatchPreparedStatementSetter() {

					public int getBatchSize() {
						return energyTimesteps.size();
					}

					@Override
					public void setValues(PreparedStatement rs, int i) throws SQLException {
						EnergyTimestep energyTimestep = energyTimesteps.get(i);
						
						rs.setInt(1, algorithmId);
						rs.setInt(2, userAppliance.getId());
						if(energyTimestep.getStartTime().getTime() >= 1289106000000L) {
							int k = 4;
						}
						rs.setLong(3, energyTimestep.getStartTime().getTime());
						rs.setLong(4, energyTimestep.getEndTime().getTime());
						rs.setInt(5, (int)energyTimestep.getEnergyConsumed());
					}});
			}
		}
	}

	public void storeUserOnOffLabels(final List<ApplianceStateTransition> detectedStateTransitions) {
		
		for(final ApplianceStateTransition transition : detectedStateTransitions) {
			KeyHolder keyHolder = new GeneratedKeyHolder();
			jdbcTemplate.update(new PreparedStatementCreator() {
					public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
			            PreparedStatement ps = connection.prepareStatement(insertApplianceStateTransition, new String[] {"id"});
			            
						ps.setInt(1, transition.getUserAppliance().getId());
						if(transition.getDetectionAlgorithmId() > 0) {
							ps.setInt(2, transition.getDetectionAlgorithmId());
						} else {
							ps.setNull(2, Types.INTEGER);
						}
						ps.setLong(3, transition.getTime());
						ps.setBoolean(4, transition.isOn());
						return ps;
			        }
			    },
			    keyHolder);
			
			transition.setId(keyHolder.getKey().intValue());
		}
	}
	
	private RowMapper<SecondData> secondDataRowMapper = new RowMapper<SecondData>() {
		public SecondData mapRow(ResultSet rs, int arg1) throws SQLException {
				SecondData data = new SecondData();
				
				long timestamp = rs.getLong("reading_time");
				//logger.debug("Got timestamp " + ts + "(" + ts.getTime() + ")");
				data.setCalLong(timestamp);
				data.setPower(rs.getInt("power"));
				data.setVoltage(rs.getFloat("voltage"));
				
				return data;
		}};

	public Date getLastMeasurementTimeForEnergyMonitor(EnergyMonitor energyMonitor) {
		return jdbcTemplate.queryForObject(queryForLastMeasurementTime, new Object[] {energyMonitor.getUserId(), energyMonitor.getType(), energyMonitor.getMonitorId()}, new RowMapper<Date>() {

			@Override
			public Date mapRow(ResultSet rs, int arg1) throws SQLException {
				long stamp = rs.getLong("max");
				if(stamp > 0) {
					return new Date(stamp);
				} else {
					return null;
				}
			}});
	}


	public List<EnergyTimestep> getApplianceEnergyConsumptionForMonitor(EnergyMonitor energyMonitor, int algorithmId, Date start, Date end) {
		final List<UserAppliance> userAppliances = getUserAppliancesForAlgorithmForEnergyMonitor(energyMonitor, algorithmId);
		
		List<EnergyTimestep> energyTimesteps = jdbcTemplate.query("select p.* from appliance_energy_consumption_predictions p, user_appliances a, energy_monitors m where p.detection_algorithm = ? and m.user_id = ? and m.monitor_type = ? and m.monitor_id = ? and m.id = a.energy_monitor_id and a.id = p.user_appliance_id and p.start_time >= ? and p.end_time < ? order by p.start_time", new Object[] { algorithmId, energyMonitor.getUserId(), energyMonitor.getType(), energyMonitor.getMonitorId(), start.getTime(), end.getTime() }, new RowMapper<EnergyTimestep>() {

			public EnergyTimestep mapRow(ResultSet rs, int arg1) throws SQLException {
				EnergyTimestep energyTimestep = new EnergyTimestep();
				
				long startTime = rs.getLong("start_time");
				long endTime = rs.getLong("end_time");
				energyTimestep.setStartTime(new Date(startTime));
				energyTimestep.setEndTime(new Date(endTime));
				energyTimestep.setEnergyConsumed(rs.getDouble("energy_consumed"));
				
				int userApplianceId = rs.getInt("user_appliance_id");
				
				for(UserAppliance app : userAppliances) {
					if(app.getId() == userApplianceId) {
						energyTimestep.setUserAppliance(app);
					}
				}
				
				return energyTimestep;
			}});
		
		return energyTimesteps;
	}
	
	@Override
	public List<ApplianceStateTransition> getUserOnOffLabels(EnergyMonitor energyMonitor) {
		return getPredictedApplianceStateTransitionsForMonitor(energyMonitor, 0, null, null, false);
	}
	
	public List<ApplianceStateTransition> getAnonymousApplianceStateTransitionsForMonitor(EnergyMonitor energyMonitor, final int algorithmId, Date start, Date end) {
		return getPredictedApplianceStateTransitionsForMonitor(energyMonitor, algorithmId, start, end, true);
	}
	
	public List<ApplianceStateTransition> getPredictedApplianceStateTransitionsForMonitor(EnergyMonitor energyMonitor, final int algorithmId, Date start, Date end, boolean anonymousAppliances) {
		final List<UserAppliance> userAppliances = getUserAppliancesForAlgorithmForEnergyMonitor(energyMonitor, algorithmId);
		
		String query = null;
		Object[] args = null;
		
		if(algorithmId != 0) {
			if(!anonymousAppliances) {
				query = "select ast.* from appliance_state_transitions ast, user_appliances ua where ast.detection_algorithm = ? and ua.energy_monitor_id = ? and ua.id = ast.user_appliance_id and ua.algorithm_generated = 0 and ast.time >= ? and ast.time < ? order by ast.time";
				args = new Object[] { algorithmId, energyMonitor.getId(), start.getTime(), end.getTime() };
			} else {
				query = "select ast.* from appliance_state_transitions ast, user_appliances ua where ast.detection_algorithm = ? and ua.energy_monitor_id = ? and ua.id = ast.user_appliance_id and ua.algorithm_generated = 1 and ast.time >= ? and ast.time < ? order by ast.time";
				args = new Object[] { algorithmId, energyMonitor.getId(), start.getTime(), end.getTime() };
			}
		} else {
			query = "select ast.* from appliance_state_transitions ast, user_appliances ua where ast.detection_algorithm is null and ua.energy_monitor_id = ? and ua.id = ast.user_appliance_id order by ast.time";
			args = new Object[] { energyMonitor.getId() };
		}
		
		List<ApplianceStateTransition> applianceStateTransitions = jdbcTemplate.query(query, args, new RowMapper<ApplianceStateTransition>() {

			public ApplianceStateTransition mapRow(ResultSet rs, int arg1) throws SQLException {
				
				int userApplianceId = rs.getInt("user_appliance_id");
				
				UserAppliance userAppliance = null;
				
				for(UserAppliance app : userAppliances) {
					if(app.getId() == userApplianceId) {
						userAppliance = app;
					}
				}
				
				return new GenericStateTransition(rs.getInt("ast.id"), userAppliance, algorithmId, rs.getBoolean("start_on"), rs.getLong("time"));
			}});
		
		return applianceStateTransitions;
	}

	public List<UserAppliance> getUserAppliancesForAlgorithmForEnergyMonitor(EnergyMonitor energyMonitor, int algorithmId) {
		return jdbcTemplate.query(queryForUserAppliancesByAlgorithmId, new Object[] { energyMonitor.getUserId(), energyMonitor.getType(), energyMonitor.getMonitorId(), algorithmId }, userApplianceMapper);
	}
	
	public List<UserAppliance> getUserAppliancesFromUserForEnergyMonitor(EnergyMonitor energyMonitor) {
		return jdbcTemplate.query(queryForUserGeneratedUserAppliances, new Object[] { energyMonitor.getUserId(), energyMonitor.getType(), energyMonitor.getMonitorId()}, userApplianceMapper);
	}
	
	public UserAppliance getUserApplianceById(int id) {
		return jdbcTemplate.queryForObject(queryForUserApplianceById, new Object[] { id }, userApplianceMapper);
	}

	public List<String> getAllUserIds() {
		return jdbcTemplate.queryForList(queryForAllUserIds, String.class);
	}

	public void addUserAppliance(final EnergyMonitor energyMonitor, final UserAppliance userAppliance) {
		logger.debug("Inserting new user appliance " + userAppliance + " for energy monitor " + energyMonitor);
		// look for energy monitor id
		int id = energyMonitor.getId();
		if(id <= 0) {
			id = getIdForEnergyMonitor(energyMonitor);
			energyMonitor.setId(id);
		}
		
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(new PreparedStatementCreator() {
				public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
		            PreparedStatement ps = connection.prepareStatement(userAppliance.getAlgorithmId() > 0 ? insertForNewUserApplianceForAlgorithm : insertForNewUserAppliance, new String[] {"id"});
		            ps.setInt(1, energyMonitor.getId());
		            ps.setLong(2, userAppliance.getAppliance().getId());
		            ps.setString(3, userAppliance.getName());
		            int next = 4;
		            if(userAppliance.getAlgorithmId() > 0) {
		            	ps.setInt(next++, userAppliance.getAlgorithmId());
		            }
		            ps.setBoolean(next++, userAppliance.isAlgorithmGenerated());
		            return ps;
		        }
		    },
		    keyHolder);
		
		userAppliance.setId(keyHolder.getKey().intValue());
	}
	
	public List<Appliance> getAllAppliances() {
		return jdbcTemplate.query("select * from appliances", applianceRowMapper);
	}
	
	@Override
	public Appliance getApplianceById(int applianceId) {
		return jdbcTemplate.queryForObject("select * from appliances where id = ?", new Object[] {applianceId}, applianceRowMapper);
	}
	
	private class ApplianceRowMapper implements RowMapper<Appliance> {
		public Appliance mapRow(ResultSet rs, int num) throws SQLException {
			
			Appliance a = new Appliance();
			a.setId(rs.getLong("id"));
			a.setDescription(rs.getString("description"));
			
			return a;
		}
	}

	@Override
	public AlgorithmResult getAlgorithmResultForMonitorAndAlgorithm(final EnergyMonitor monitor, final ApplianceEnergyConsumptionDetectionAlgorithm algorithm) {
		try {
			return jdbcTemplate.queryForObject("select * from algorithm_models where energy_monitor_id = ? and detection_algorithm = ?", new Object[] {monitor.getId(), algorithm.getId()}, new RowMapper<AlgorithmResult>() {

				@Override
				public AlgorithmResult mapRow(ResultSet rs, int arg1) throws SQLException {
					byte[] modelResult = getModelResultFromDisk(monitor.getId());
					Serializable result = null;
					
					try {
						Object tokenObject = SerializationUtils.deserialize(modelResult);
						result = (Serializable)tokenObject;
					} catch (Exception e) {
						throw new RuntimeException("Could not deserialize blob into algorithm result", e);
					}
					
					return new AlgorithmResult(monitor, algorithm, result);
				}

				private byte[] getModelResultFromDisk(int id) {
					try {
						return FileUtils.readFileToByteArray(getResultFile(id));
					}
					catch (IOException e) {
						logger.warn("Could not read model from disk", e);
						return null;
					}
				}});
		} catch (EmptyResultDataAccessException e) {
			// no algorithm result
			return null;
		}
	}

	@Override
	public Map<UserAppliance, ApplianceStateTransition> getLatestApplianceStatesForUserAppliances(List<UserAppliance> apps, final ApplianceEnergyConsumptionDetectionAlgorithm algorithm) {
		
		List<ApplianceStateTransition> latestTransitions = new ArrayList<ApplianceStateTransition>();
		
		// query will not execute properly if there are no appliance ids in the "in" list
		if(apps.size() > 0) {
			List<Integer> applianceIds = new ArrayList<Integer>();
			for(UserAppliance userAppliance : apps) {
				applianceIds.add(userAppliance.getId());
			}
		
			MapSqlParameterSource parameters = new MapSqlParameterSource();
			parameters.addValue("user_appliance_ids", applianceIds);
			parameters.addValue("detection_algorithm", algorithm.getId());
	
			NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(jdbcTemplate);
			
			ApplianceStateTransitionMapper applianceStateTransitionMapper = new ApplianceStateTransitionMapper(algorithm);
			template.query("select ast.id as ast_id, ast.start_on as ast_start_on, ast.time as ast_time, ua.id as ua_id, ua.name as ua_name, ua.algorithm_generated as ua_algorithm_generated from appliance_state_transitions ast, (select user_appliance_id, max(time) as maxdatetime from appliance_state_transitions where user_appliance_id in (:user_appliance_ids) and detection_algorithm = :detection_algorithm group by user_appliance_id) groupedast, user_appliances ua where ua.id = ast.user_appliance_id and ast.user_appliance_id = groupedast.user_appliance_id and ast.time = groupedast.maxdatetime order by ast_time", parameters, applianceStateTransitionMapper);
		}
		
		Map<UserAppliance, ApplianceStateTransition> latestApplianceStateTransitions = new HashMap<UserAppliance, ApplianceStateTransition>();
		
		for(ApplianceStateTransition transition : latestTransitions) {
			latestApplianceStateTransitions.put(transition.getUserAppliance(), transition);
		}
		
		return latestApplianceStateTransitions;
	}

	@Override
	public void saveAlgorithmResult(final AlgorithmResult result) {
		int id = -1;
		try {
			id = jdbcTemplate.queryForInt("select id from algorithm_models where energy_monitor_id = ? and detection_algorithm = ?", new Object[] {result.getMonitor().getId(), result.getAlgorithm().getId()});
		} catch (EmptyResultDataAccessException e) {
			// no model saved yet - no problem
		}
		
		byte[] bytes = SerializationUtils.serialize(result.getResult());
		
		if(result.getResult() instanceof byte[]) {
			logger.debug("Attempting to save model with " + ((byte[])result.getResult()).length + " bytes");
		}
		
		if(id > 0) {
			// algorithm row already exists - update
			FileUtils.deleteQuietly(getResultFile(result.getMonitor().getId()));
		} else {
			// does not exist - add new row
			jdbcTemplate.update("insert into algorithm_models (energy_monitor_id, detection_algorithm) values (?, ?)", new Object[] {result.getMonitor().getId(), result.getAlgorithm().getId()});
		}

		try {
			FileUtils.writeByteArrayToFile(getResultFile(result.getMonitor().getId()), bytes);
		}
		catch (IOException e) {
			throw new RuntimeException("Could not save model result to file", e);
		}
	}

	@Override
	public void removeStateTransitionAndEnergyPredictionsForAlgorithmAndMonitor(ApplianceEnergyConsumptionDetectionAlgorithm algorithm, EnergyMonitor energyMonitor) {
		jdbcTemplate.update("delete ast.* from appliance_state_transitions ast inner join user_appliances ua where ast.detection_algorithm = ? and ua.id = ast.user_appliance_id and ua.energy_monitor_id = ?", new Object[] {algorithm.getId(), energyMonitor.getId()});
		jdbcTemplate.update("delete aecp.* from appliance_energy_consumption_predictions aecp inner join user_appliances ua where aecp.detection_algorithm = ? and ua.id = aecp.user_appliance_id and ua.energy_monitor_id = ?", new Object[] {algorithm.getId(), energyMonitor.getId()});
		jdbcTemplate.update("delete from user_appliances where algorithm_generated = 1 and algorithm_id = ? and energy_monitor_id = ?", new Object[] {algorithm.getId(), energyMonitor.getId()});
	}

	private File getResultFile(int id) {
		return new File(new File(new File(System.getProperty("user.home"), ".appenergy"),"modelResults"), id + "");
	}

	@Override
	public EnergyMonitor getEnergyMonitor(String userId, String monitorId, String monitorType) {
		try {
			return jdbcTemplate.queryForObject("select * from energy_monitors where user_id = ? and monitor_type = ? and monitor_id = ?", new Object[] { userId, monitorType, monitorId }, energyMonitorRowMapper);
		} catch (EmptyResultDataAccessException e) {
			return null;
		}	
	}

	@Override
	public float getEnergyCost(EnergyMonitor energyMonitor) {
		float cost = -1;
		
		try {
			cost = jdbcTemplate.queryForObject("select cost_per_kwh from energy_costs where energy_monitor_id = ?", new Object[] {energyMonitor.getId()}, new RowMapper<Float>() {
				@Override
				public Float mapRow(ResultSet rs, int arg1) throws SQLException {
					return rs.getFloat("cost_per_kwh");
				}});
		} catch (EmptyResultDataAccessException e) {
			
		}
		
		return cost;
	}

	@Override
	public void setEnergyCost(EnergyMonitor energyMonitor, float costPerKwh) {
		jdbcTemplate.update("insert into energy_costs (energy_monitor_id, cost_per_kwh) values (?, ?) on duplicate key update cost_per_kwh = values(cost_per_kwh)", new Object[] {energyMonitor.getId(), costPerKwh});
	}

	@Override
	public Map<Appliance,Double> getApplianceEnergyConsumptionAverages(Date startDate, int detectionAlgorithmId, EnergyMonitor ignoredMonitor) {
		final Map<Appliance,Double> applianceMap = new HashMap<Appliance,Double>();
		
		jdbcTemplate.query(queryForApplianceEnergyConsumptionAverages, new Object[] {detectionAlgorithmId, startDate.getTime(), ignoredMonitor.getId()}, new RowMapper<Entry<Appliance,Double>>() {
			@Override
			public Entry<Appliance, Double> mapRow(ResultSet rs, int arg1) throws SQLException {
				// just put entries into the map
				applianceMap.put(getApplianceById(rs.getInt("appliance_id")),rs.getDouble("average_energy_consumed"));
				return null;
			}});
		
		return applianceMap;
	}

	@Override
	public GenericStateTransition getAnonymousApplianceStateTransitionById(final int transitionId) {
		return jdbcTemplate.queryForObject("select * from appliance_state_transitions where id = ?", new Object[] {transitionId}, new RowMapper<GenericStateTransition>() {

			@Override
			public GenericStateTransition mapRow(ResultSet rs, int arg1) throws SQLException {
				return new GenericStateTransition(transitionId, null, rs.getInt("detection_algorithm"), rs.getBoolean("start_on"), rs.getLong("time"));
			}});
	}

	@Override
	public void removeTransition(GenericStateTransition transition) {
		jdbcTemplate.update("delete from appliance_state_transitions where id = ?", new Object[] {transition.getId()});
	}		
}
