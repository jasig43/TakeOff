package com.takeoff.backend;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.mock.env.MockEnvironment;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.config.TakeoffProperties.Admin;
import com.takeoff.backend.config.TakeoffProperties.Cors;
import com.takeoff.backend.config.TakeoffProperties.Jwt;
import com.takeoff.backend.config.TakeoffProperties.Otp;
import com.takeoff.backend.config.TakeoffProperties.Rabbit;
import com.takeoff.backend.config.TakeoffProperties.Seed;
import com.takeoff.backend.config.TakeoffProperties.Sms;
import com.takeoff.backend.config.TakeoffProperties.TestBypass;
import com.takeoff.backend.config.TakeoffProperties.Twilio;

/** Builders shared by the plain (non-Spring) unit tests. */
public final class TestFixtures {

	public static final String JWT_SECRET = "test-only-jwt-secret-0123456789-0123456789";
	public static final String COMPLIANT_PASSWORD = "Sturdy#Password2026";
	public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

	private TestFixtures() {
	}

	public static Twilio noTwilio() {
		return new Twilio("", "", "", "", "https://api.twilio.com");
	}

	public static TakeoffProperties properties(boolean hashCodes, boolean bypassEnabled) {
		return new TakeoffProperties(new Jwt(JWT_SECRET, 15, "takeoff-backend"),
				new Cors(List.of("http://localhost:5173")),
				new Otp(300, 30, 5, 5, hashCodes, "test-pepper", false,
						new TestBypass(bypassEnabled, "+15550199", "123456")),
				new Rabbit("takeoff.exchange", "otp.queue", "otp.routing.key"),
				new Admin(new Seed(false, "", "", "", "")), new Sms("none", noTwilio()));
	}

	/** Same as {@link #properties(boolean, boolean)} but with a different JWT secret. */
	public static TakeoffProperties withJwtSecret(String secret) {
		TakeoffProperties base = properties(false, false);
		return new TakeoffProperties(new Jwt(secret, 15, "takeoff-backend"), base.cors(), base.otp(), base.rabbitmq(),
				base.admin(), base.sms());
	}

	/** Same as {@link #properties(boolean, boolean)} but with a different SMS configuration. */
	public static TakeoffProperties withSms(String provider, Twilio twilio) {
		TakeoffProperties base = properties(false, false);
		return new TakeoffProperties(base.jwt(), base.cors(), base.otp(), base.rabbitmq(), base.admin(),
				new Sms(provider, twilio));
	}

	public static MockEnvironment environment(String... activeProfiles) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(activeProfiles);
		return environment;
	}
}
