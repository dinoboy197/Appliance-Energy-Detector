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
package info.raack.appliancelabeler.service;

import java.io.ByteArrayInputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import edu.cmu.hcii.stepgreen.data.ted.SecondDataParser;
import edu.cmu.hcii.stepgreen.data.ted.data.SecondData;
import edu.cmu.hcii.stepgreen.data.teds.AggregatedTeds;
import edu.cmu.hcii.stepgreen.data.teds.LinkType;
import edu.cmu.hcii.stepgreen.data.teds.Mtu;
import edu.cmu.hcii.stepgreen.data.teds.Ted5000;


public class BackupDatabase {
	
	private String queryForAggregatedTeds = "select t.id as tedid, u.login as username from ted5000s t, users u where t.user_id = u.id and t.fetch = '1'";
	private String queryForMtusForTed = "select id as mtuid from ted5000_mtus where ted5000_id = ?";
	private String queryForSecondData = "select raw from ted5000_mtu_second_datas where ted5000_mtu_id = ?";
	
	private SecondDataParser secondDataParser = new SecondDataParser();
	
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	@Qualifier("backupDatasource")
    public void init(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

	public List<SecondData> getSecondDataFromBackupDatabase(String userId, Ted5000 ted, Mtu mtu, String datapointsBack) {
		List<SecondData> data = jdbcTemplate.query(queryForSecondData, new Object[] {mtu.getId()}, new RowMapper<SecondData>() {

			public SecondData mapRow(ResultSet rs, int arg1) throws SQLException {
				return secondDataParser.parse(new ByteArrayInputStream(rs.getString("raw").getBytes())).get(0);
			}});
		
		return data;
	}

	public AggregatedTeds getConfigurationsFromBackupDatabase() {
		List<Ted5000> teds = jdbcTemplate.query(queryForAggregatedTeds, new RowMapper<Ted5000>() {

			public Ted5000 mapRow(ResultSet rs, int arg1) throws SQLException {
				Ted5000 ted = new Ted5000();
				
				ted.setId(rs.getString("tedid"));
				
				LinkType link = new LinkType();
				link.setRel("owner");
				link.setHref("dummy/users/" + rs.getString("username") + ".xml");
				ted.getLink().add(link);
				
				return ted;
			}});
		
		for(Ted5000 ted : teds) {
			// get mtus
			ted.getTed5000Mtu().addAll(jdbcTemplate.query(queryForMtusForTed, new Object[] {ted.getId()}, new RowMapper<Mtu>() {

				public Mtu mapRow(ResultSet rs, int arg1) throws SQLException {
					Mtu mtu = new Mtu();
					
					mtu.setId(rs.getString("mtuid"));
					
					return mtu;
				}}));
		}
		
		AggregatedTeds at = new AggregatedTeds();
		at.getTed5000().addAll(teds);
		
		return at;
	}

}
