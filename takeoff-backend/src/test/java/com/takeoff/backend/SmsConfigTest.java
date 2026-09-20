package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.takeoff.backend.config.SmsConfig;
import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.config.TakeoffProperties.Twilio;
import com.takeoff.backend.sms.LoggingSmsSender;
import com.takeoff.backend.sms.SmsSender;
import com.takeoff.backend.sms.TwilioSmsSender;

class SmsConfigTest {

	/** A builder bound to a mock server: no real HTTP client is created (unit tests must not need a network stack). */
	private final SmsConfig config = new SmsConfig(() -> {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer.bindTo(builder).build();
		return builder;
	});

	@Test
	void defaultsToTheNonSendingLoggerWhenNoProviderIsConfigured() {
		SmsSender sender = config.smsSender(TestFixtures.withSms("none", TestFixtures.noTwilio()),
				TestFixtures.environment("dev"));
		assertThat(sender).isInstanceOf(LoggingSmsSender.class);
		assertThat(config.smsSender(TestFixtures.withSms(null, TestFixtures.noTwilio()), TestFixtures.environment("prod")))
			.isInstanceOf(LoggingSmsSender.class);
	}

	@Test
	void selectsTwilioCaseInsensitivelyWhenConfigured() {
		Twilio twilio = new Twilio("AC1", "token", "+15005550006", "", "https://api.twilio.com");
		assertThat(config.smsSender(TestFixtures.withSms("twilio", twilio), TestFixtures.environment("prod")))
			.isInstanceOf(TwilioSmsSender.class);
		assertThat(config.smsSender(TestFixtures.withSms(" Twilio ", twilio), TestFixtures.environment("prod")))
			.isInstanceOf(TwilioSmsSender.class);
	}

	@Test
	void failsFastWhenTwilioIsSelectedWithoutCredentials() {
		assertThatThrownBy(() -> config.smsSender(TestFixtures.withSms("twilio", TestFixtures.noTwilio()),
				TestFixtures.environment("prod")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("TWILIO_ACCOUNT_SID");
	}

	@Test
	void rejectsAnUnknownProvider() {
		assertThatThrownBy(() -> config.smsSender(TestFixtures.withSms("carrier-pigeon", TestFixtures.noTwilio()),
				TestFixtures.environment("dev")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("carrier-pigeon")
			.hasMessageContaining("twilio");
	}

	@Test
	void secretsNeverAppearInToString() {
		TakeoffProperties properties = TestFixtures.withSms("twilio",
				new Twilio("AC1", "very-secret-auth-token", "+15005550006", "", "https://api.twilio.com"));

		assertThat(properties.sms().twilio().toString()).doesNotContain("very-secret-auth-token").contains("redacted");
		assertThat(properties.jwt().toString()).doesNotContain(TestFixtures.JWT_SECRET);
		assertThat(properties.otp().toString()).doesNotContain("test-pepper");
	}
}
