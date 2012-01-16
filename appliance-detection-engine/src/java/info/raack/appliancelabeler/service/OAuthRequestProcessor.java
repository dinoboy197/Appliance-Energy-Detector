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

import info.raack.appliancelabeler.util.OAuthUnauthorizedException;
import info.raack.appliancelabeler.util.ResponseHandler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

import javax.annotation.PostConstruct;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth.consumer.OAuthRestTemplate;
import org.springframework.security.oauth.consumer.OAuthSecurityContext;
import org.springframework.security.oauth.consumer.OAuthSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import edu.cmu.hcii.stepgreen.data.connector.StepgreenConnector;

@Component
public class OAuthRequestProcessor {
	private Logger logger = LoggerFactory.getLogger(OAuthRequestProcessor.class);
	
	@Autowired
	private OAuthRestTemplate oAuthRestTemplate;
	
	private Unmarshaller u1;
	private Unmarshaller u2;
	
	@PostConstruct
	public void init() {
		try {
			JAXBContext jc = JAXBContext.newInstance( "edu.cmu.hcii.stepgreen.data.teds" );
			u1 = jc.createUnmarshaller();
			SchemaFactory sf = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
			URL url = this.getClass().getClassLoader().getResource("teds.xsd");
			Schema schema = sf.newSchema(url);
			u1.setSchema(schema);
			
			jc = JAXBContext.newInstance( "edu.cmu.hcii.stepgreen.data.base" );
			u2 = jc.createUnmarshaller();
			sf = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
			url = this.getClass().getClassLoader().getResource("stepgreen.xsd");
			schema = sf.newSchema(url);
			u2.setSchema(schema);
			
		} catch (Exception e) {
			throw new RuntimeException("Could not create JAXB unmarshaller", e);
		}
	}
	
	public <T,S> S processRequest(String uri, ResponseHandler<T,S> handler) throws OAuthUnauthorizedException {
		return processRequest(uri, null, handler);
	}
	
	public <T,S> S processRequest(String uri, OAuthSecurityContext context, ResponseHandler<T,S> handler) throws OAuthUnauthorizedException {
		logger.debug("Attempting to request " + uri);
		
		String responseString = null;
		InputStream xmlInputStream = null;
		try {
			if(context != null) {
				// set the current authentication context
				OAuthSecurityContextHolder.setContext(context);
			}
			
			// use the normal request processor for the currently logged in user
			byte[] bytes = oAuthRestTemplate.getForObject(URI.create(uri), byte[].class);
			
			responseString = new String(bytes);
			//logger.debug(new String(bytes));
			xmlInputStream = new ByteArrayInputStream(bytes);
			
			//logger.debug("response: " + new String(bytes));
			synchronized(this) {
				try {
					T item = (T)((JAXBElement)u1.unmarshal(xmlInputStream)).getValue();
					return handler.extractValue(item);
				} catch (Exception e) {
					// don't do anything if we can't unmarshall with the teds.xsd - try the other one
					try {
						xmlInputStream.close();
					} catch (Exception e2) {
						
					}

					xmlInputStream = new ByteArrayInputStream(bytes);
				}
				T item = (T)((JAXBElement)u2.unmarshal(xmlInputStream)).getValue();
				return handler.extractValue(item);
			}
			
		} catch (HttpClientErrorException e2) {
			// if unauthorized - our credentials are bad or have been revoked - throw exception up the stack
			if(e2.getStatusCode() == HttpStatus.UNAUTHORIZED) {
				throw new OAuthUnauthorizedException();
			} else {
				throw new RuntimeException("Unknown remote server error " + e2.getStatusCode() + " (" + e2.getStatusText() + ") returned when requesting " + uri + "; " + e2.getResponseBodyAsString());
			}
		} catch (HttpServerErrorException e3) {
			throw new RuntimeException("Unknown remote server error " + e3.getStatusCode() + " (" + e3.getStatusText() + ") returned when requesting " + uri + "; " + e3.getResponseBodyAsString());
		} catch (Exception e) {
			throw new RuntimeException("Could not request " + uri + (responseString != null ? " response was " + responseString : ""), e);
		}
		finally {
			try {
				if(xmlInputStream != null) {
					xmlInputStream.close();
				}
			} catch (Exception e) { }
		}
	}
	
	
}
