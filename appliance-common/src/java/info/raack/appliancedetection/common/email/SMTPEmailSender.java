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
package info.raack.appliancedetection.common.email;

import info.raack.appliancedetection.common.util.StoppableThread;
import info.raack.appliancedetection.common.util.StoppableThreadAction;

import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.PreDestroy;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SMTPEmailSender implements EmailSender {
	
	private Logger logger = LoggerFactory.getLogger(SMTPEmailSender.class);
	
	@Value("${email.username}")
	private String username;

	@Value("${email.password}")
	private String password;

	@Value("${email.from}")
	private String from;
	
	@Value("${environment}")
	private String environment;

	private BlockingQueue<Message> emailQueue;
	private StoppableThread emailThread;

	private Properties emailProperties;

	public SMTPEmailSender() {
		emailProperties = new Properties();
		emailProperties.put("mail.smtp.host", "smtp.gmail.com");
		emailProperties.put("mail.smtp.socketFactory.port", "465");
		emailProperties.put("mail.smtp.socketFactory.class",
				"javax.net.ssl.SSLSocketFactory");
		emailProperties.put("mail.smtp.auth", "true");
		emailProperties.put("mail.smtp.port", "465");


		emailQueue = new LinkedBlockingQueue<Message>();
		
		emailThread = new StoppableThread(new StoppableThreadAction() {

			public void doAction() throws InterruptedException {
				sendGeneralEmail(emailQueue.take());
			}
			
			private void sendGeneralEmail(Message message) {
				try {
					logger.debug("Sending email to " + message.getRecipients(Message.RecipientType.TO)[0]);
					Transport.send(message);
				} catch (Exception e) {
					throw new RuntimeException("Could not send email", e);
				}
			}

			public String getName() {
				return "email sender";
			}});
		
		emailThread.start();
	}

	public void sendGeneralEmail(String recipientEmail, String subject, String body) {
		Session session = Session.getDefaultInstance(emailProperties,
				new javax.mail.Authenticator() {
					protected PasswordAuthentication getPasswordAuthentication() {
						return new PasswordAuthentication(username,
								password);
					}
				});

		Message message = new MimeMessage(session);

		try {
			message.setFrom(new InternetAddress(from));
			message.setRecipients(Message.RecipientType.TO, InternetAddress
					.parse(recipientEmail));
			message.setSubject(subject + (!environment.equals("prod") ? " [" + environment + "]" : ""));
			message.setText(body);
		} catch (Exception e) {
			throw new RuntimeException("Could not create message", e);
		}

		emailQueue.add(message);
	}
	
	@PreDestroy
	public void beforeDestroying() {
		emailThread.endProcessing();
	}
}
