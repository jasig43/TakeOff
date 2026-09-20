package com.takeoff.backend.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.takeoff.backend.sms.LoggingSmsSender;
import com.takeoff.backend.sms.SmsSender;
import com.takeoff.backend.sms.TwilioSmsSender;

/** Chooses the SMS provider from {@code takeoff.sms.provider}. */
@Configuration(proxyBeanMethods = false)
public class SmsConfig {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	private final Supplier<RestClient.Builder> restClientBuilder;

	public SmsConfig() {
		this(SmsConfig::timeoutRestClientBuilder);
	}

	/** Lets tests supply a builder bound to a mock server instead of a real HTTP client. */
	public SmsConfig(Supplier<RestClient.Builder> restClientBuilder) {
		this.restClientBuilder = restClientBuilder;
	}

	@Bean
	public SmsSender smsSender(TakeoffProperties properties, Environment environment) {
		String provider = properties.sms().provider() == null ? "none" : properties.sms().provider().trim().toLowerCase(Locale.ROOT);
		return switch (provider) {
			case "twilio" -> {
				// Validate before building an HTTP client so a missing credential is reported as such.
				TwilioSmsSender.requireConfigured(properties.sms().twilio());
				yield new TwilioSmsSender(properties.sms().twilio(), restClientBuilder.get());
			}
			case "none", "" -> new LoggingSmsSender(environment.acceptsProfiles(Profiles.of("dev", "test")));
			default -> throw new IllegalStateException(
					"Unknown takeoff.sms.provider '" + provider + "'. Supported values: twilio, none.");
		};
	}

	/** Bounded timeouts: a slow SMS provider must never hang a RabbitMQ listener thread. */
	static RestClient.Builder timeoutRestClientBuilder() {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return RestClient.builder().requestFactory(requestFactory);
	}
}
