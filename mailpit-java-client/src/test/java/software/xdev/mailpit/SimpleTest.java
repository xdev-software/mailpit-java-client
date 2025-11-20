/*
 * Copyright © 2025 XDEV Software (https://xdev.software)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package software.xdev.mailpit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.api.mailer.config.TransportStrategy;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;

import software.xdev.mailpit.api.MessagesApi;
import software.xdev.mailpit.client.ApiClient;
import software.xdev.mailpit.container.MailpitContainer;
import software.xdev.mailpit.model.Address;
import software.xdev.mailpit.model.MessageSummary;


class SimpleTest
{
	@Test
	void check()
	{
		final String user = "test@test.localhost";
		final String pw = "test";
		try(final MailpitContainer mailpitContainer = new MailpitContainer()
			.allowInsecure()
			.withSmtpAuth(Map.of(user, pw)))
		{
			mailpitContainer.start();
			
			final String to = "receiver@test.localhost";
			final String subject = "Test Subject";
			final String plainText = "Test Plain Text";
			
			try(final Mailer mailer = MailerBuilder.withSMTPServer(
					mailpitContainer.getHost(),
					mailpitContainer.getMappedPort(MailpitContainer.SMTP_PORT),
					user,
					pw)
				.withTransportStrategy(TransportStrategy.SMTP)
				.buildMailer())
			{
				mailer.sendMail(
					EmailBuilder.startingBlank()
						.from(user)
						.to(to)
						.withSubject(subject)
						.withPlainText(plainText)
						.buildEmail());
			}
			catch(final Exception ex)
			{
				Assertions.fail(ex);
			}
			
			final ApiClient apiClient = createApiClient(mailpitContainer);
			final List<MessageSummary> messages = new MessagesApi(apiClient)
				.getMessagesParams(null, null)
				.getMessages();
			assertEquals(1, messages.size());
			
			final MessageSummary message = messages.stream().findFirst().orElseThrow();
			
			Assertions.assertAll(
				() -> assertEquals(user, message.getFrom().getAddress()),
				() -> assertEquals(to, message.getTo().stream().findFirst().map(Address::getAddress).orElse(null)),
				() -> assertEquals(subject, message.getSubject()),
				() -> assertEquals(plainText, message.getSnippet())
			);
		}
	}
	
	private static ApiClient createApiClient(final MailpitContainer container)
	{
		final Duration defaultTimeout = Duration.ofSeconds(30);
		
		final ApiClient client = new ApiClient();
		client.setHttpClient(HttpClientBuilder.create()
			.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
				.setDefaultConnectionConfig(ConnectionConfig.custom()
					.setConnectTimeout(Timeout.of(defaultTimeout))
					.setSocketTimeout(Timeout.of(defaultTimeout))
					.build())
				.build())
			.build());
		client.setBasePath("http://" + container.getHost() + ":" + container.getMappedPort(MailpitContainer.WEB_PORT));
		return client;
	}
}
