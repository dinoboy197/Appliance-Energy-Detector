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
package info.raack.appliancelabeler.security;

import info.raack.appliancelabeler.service.DataService;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth.consumer.rememberme.HttpSessionOAuthRememberMeServices;
import org.springframework.security.oauth.consumer.token.OAuthConsumerToken;
import org.springframework.stereotype.Component;

@Component("httpSessionAndDatabaseOAuthRememberMeServices")
public class HttpSessionAndDatabaseOAuthRemeberMeServices extends HttpSessionOAuthRememberMeServices {
	private Logger logger = LoggerFactory.getLogger(HttpSessionAndDatabaseOAuthRemeberMeServices.class);
	
	public static final String EMAIL_ATTRIBUTE = "email";
	
	@Autowired
	private DataService dataService;
	
	
	public Map<String, OAuthConsumerToken> loadRememberedTokens(HttpServletRequest request, HttpServletResponse response) {
		// check httpsessionrememberme services first
		
		Map<String,OAuthConsumerToken> tokens = super.loadRememberedTokens(request, response);
		
		if(tokens != null) {
			logger.debug("Found existing oauth tokens in session");
			return tokens;
		}
		else {
			// haven't found any tokens yet - look in the database
			
			// ASSUMPTIONS - remember tokens is called with every token request (spring security oauth code), so any tokens in the session will also be in the database
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			
			String userId = null;
			if(auth != null && auth.isAuthenticated()) {
				if(auth instanceof RememberMeAuthenticationToken) {
					Object principal = auth.getPrincipal();
					if(principal instanceof OAuthUserDetails) {
						logger.debug("Found existing oauth tokens in remember me persistence");
						return ((OAuthUserDetails)principal).getOAuthTokens();
					} else if (principal instanceof String) {
						logger.debug("Found user id in remember me persistence; grabbing oauth tokens from database");
						return dataService.getOAuthTokensForUserId((String)principal);
					}
				}
				else if(auth instanceof OAuthAutomaticAuthenticationToken) {
					// user is already logged in via spring security
					logger.debug("Found user id in oauth automatic login token; grabbing oauth tokens from database");
					return dataService.getOAuthTokensForUserId((String)auth.getPrincipal());
				}
			}
			return null;
		}
	}

	public void rememberTokens(Map<String, OAuthConsumerToken> tokens, HttpServletRequest request, HttpServletResponse response) {
		// put tokens into session
		
		String email = "";
		
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.setAttribute(REMEMBERED_TOKENS_KEY, tokens);
			email = (String)session.getAttribute(EMAIL_ATTRIBUTE);
		}
		
		// put tokens into database
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if(auth != null && auth.isAuthenticated()) {
			String userId = null;
			
			if(auth instanceof RememberMeAuthenticationToken) {
				Object principal = auth.getPrincipal();
				if(principal instanceof OAuthUserDetails) {
					userId = ((OAuthUserDetails)principal).getUsername();
				} else if (principal instanceof String) {
					userId = (String)auth.getPrincipal();
				}
			}
			else if(auth instanceof OAuthAutomaticAuthenticationToken) {
				// user is already logged in via spring security
				userId = (String)auth.getPrincipal();
			}
			
			logger.debug("Saving oauth tokens to database");
			if(userId != null) {
				dataService.saveOAuthTokensForUserId(userId, email, tokens);
			}
		}
		
	}

}
