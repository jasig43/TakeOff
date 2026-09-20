package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.takeoff.backend.config.TakeoffProperties.Twilio;
import com.takeoff.backend.sms.SmsDeliveryException;
import com.takeoff.backend.sms.TwilioSmsSender;

/**
 * Verifies the exact HTTP request Twilio expects and how failures are reported. Uses Spring's mock HTTP server,
 * so no real Twilio account or network access is involved.
 */
class TwilioSmsSenderTest {

	private static final String BASE = "https://api.twilio.test";
	private static final String MESSAGES_URL = BASE + "/2010-04-01/Accounts/AC1234/Messages.json";
	private static final String TO = "+263771234567";
	private static final String BODY = "Your TakeOFF verification code is 482913. It expires in 5 minutes.";

	private RestClient.Builder builder;
	private MockRestServiceServer server;

	@BeforeEach
	void setUp() {
		builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
	}

	private TwilioSmsSender sender(String from, String messagingServiceSid, String baseUrl) {
		return new TwilioSmsSender(new Twilio("AC1234", "s3cret-token", from, messagingServiceSid, baseUrl), builder);
	}

	private static RestClient.Builder mockedBuilder() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer.bindTo(builder).build();
		return builder;
	}

	private static MultiValueMap<String, String> form(String... keyValues) {
		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.add(keyValues[i], keyValues[i + 1]);
		}
		return map;
	}

	@Test
	void postsAFormWithBasicAuthToTheMessagesEndpoint() {
		String expectedAuth = "Basic " + Base64.getEncoder().encodeToString("AC1234:s3cret-token".getBytes(StandardCharsets.UTF_8));
		server.expect(requestTo(MESSAGES_URL))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", expectedAuth))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
			.andExpect(content().formData(form("To", TO, "From", "+15005550006", "Body", BODY)))
			.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"sid\":\"SM123\"}"));

		sender("+15005550006", "", BASE).send(TO, BODY);

		server.verify();
	}

	@Test
	void usesTheMessagingServiceInsteadOfAFromNumberWhenConfigured() {
		server.expect(requestTo(MESSAGES_URL))
			.andExpect(content().formData(form("To", TO, "MessagingServiceSid", "MG999", "Body", BODY)))
			.andRespond(withStatus(HttpStatus.CREATED));

		sender("+15005550006", "MG999", BASE).send(TO, BODY);

		server.verify();
	}

	@Test
	void toleratesATrailingSlashOnTheBaseUrl() {
		server.expect(requestTo(MESSAGES_URL)).andRespond(withStatus(HttpStatus.CREATED));

		sender("+15005550006", "", BASE + "/").send(TO, BODY);

		server.verify();
	}

	@Test
	void aRejectedMessageBecomesADeliveryExceptionWithoutLeakingNumberBodyOrCredentials() {
		server.expect(requestTo(MESSAGES_URL))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":21211,\"message\":\"The 'To' number +263771234567 is not a valid phone number.\","
						+ "\"status\":400}"));

		assertThatThrownBy(() -> sender("+15005550006", "", BASE).send(TO, BODY)).isInstanceOf(SmsDeliveryException.class)
			.hasMessageContaining("HTTP 400")
			.hasMessageContaining("Twilio error 21211")
			.satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("263771234567")
				.doesNotContain("482913")
				.doesNotContain("s3cret-token")
				.doesNotContain("not a valid"));
	}

	@Test
	void aProviderOutageBecomesADeliveryException() {
		server.expect(requestTo(MESSAGES_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

		assertThatThrownBy(() -> sender("+15005550006", "", BASE).send(TO, BODY)).isInstanceOf(SmsDeliveryException.class)
			.hasMessageContaining("HTTP 503");
	}

	@Test
	void aNetworkFailureBecomesADeliveryException() {
		server.expect(requestTo(MESSAGES_URL)).andRespond(request -> {
			throw new IOException("connection reset");
		});

		assertThatThrownBy(() -> sender("+15005550006", "", BASE).send(TO, BODY)).isInstanceOf(SmsDeliveryException.class)
			.hasMessageContaining("Could not reach Twilio");
	}

	@Test
	void refusesToStartWithoutCredentialsOrASender() {
		assertThatThrownBy(() -> new TwilioSmsSender(new Twilio("", "token", "+1500", "", BASE), mockedBuilder()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TWILIO_ACCOUNT_SID");
		assertThatThrownBy(() -> new TwilioSmsSender(new Twilio("AC1", "", "+1500", "", BASE), mockedBuilder()))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new TwilioSmsSender(new Twilio("AC1", "token", "", "", BASE), mockedBuilder()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TWILIO_FROM_NUMBER");
		assertThatCode(() -> new TwilioSmsSender(new Twilio("AC1", "token", "", "MG1", BASE), mockedBuilder()))
			.doesNotThrowAnyException();
	}
}
