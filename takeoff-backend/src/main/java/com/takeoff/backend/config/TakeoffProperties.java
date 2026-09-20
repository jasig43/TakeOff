package com.takeoff.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code takeoff.*} configuration. Values come from application.yml and are
 * overridable through environment variables (see .env.example).
 *
 * <p>Records containing secrets override {@code toString()} so they can never leak through a log line.
 */
@ConfigurationProperties(prefix = "takeoff")
public record TakeoffProperties(Jwt jwt, Cors cors, Otp otp, Rabbit rabbitmq, Admin admin, Driver driver, Sms sms,
		Storage storage) {

	public record Jwt(String secret, long expirationMinutes, String issuer) {

		@Override
		public String toString() {
			return "Jwt[secret=<redacted>, expirationMinutes=" + expirationMinutes + ", issuer=" + issuer + "]";
		}
	}

	public record Cors(List<String> allowedOrigins) {
	}

	public record Otp(long expirationSeconds, long resendCooldownSeconds, int maxAttempts, int maxSendsPerHour,
			boolean hashCodes, String pepper, boolean logToConsole, TestBypass testBypass) {

		@Override
		public String toString() {
			return "Otp[expirationSeconds=" + expirationSeconds + ", pepper=<redacted>]";
		}
	}

	/** Evaluator shortcut: {@code phoneNumber} always receives {@code code}. Refused outside dev/test. */
	public record TestBypass(boolean enabled, String phoneNumber, String code) {

		@Override
		public String toString() {
			return "TestBypass[enabled=" + enabled + "]";
		}
	}

	/**
	 * One exchange, two queues: {@code queue} carries OTP requests, {@code notificationQueue} carries
	 * "application decided" events for the notification listener.
	 */
	public record Rabbit(String exchange, String queue, String routingKey, String notificationQueue,
			String notificationRoutingKey) {
	}

	public record Admin(Seed seed) {
	}

	/** An optional ready-made driver account for demos and testing (dev/test profiles only). */
	public record Driver(Seed seed) {
	}

	public record Seed(boolean enabled, String email, String password, String phoneNumber, String fullName) {

		@Override
		public String toString() {
			return "Seed[enabled=" + enabled + ", email=" + email + ", password=<redacted>]";
		}
	}

	/**
	 * How verification codes reach the user's phone.
	 *
	 * @param provider {@code none} (codes are not sent; in dev/test they are printed to the console) or
	 *                 {@code twilio}
	 */
	public record Sms(String provider, Twilio twilio) {
	}

	/**
	 * Twilio Programmable SMS credentials. Set either {@code fromNumber} (a Twilio phone number, or an
	 * approved alphanumeric sender id) or {@code messagingServiceSid}; the messaging service wins if both are set.
	 */
	public record Twilio(String accountSid, String authToken, String fromNumber, String messagingServiceSid,
			String apiBaseUrl) {

		@Override
		public String toString() {
			return "Twilio[accountSid=" + accountSid + ", authToken=<redacted>]";
		}
	}

	/** Where uploaded application documents are stored on disk, and the largest file accepted. */
	public record Storage(String uploadDir, long maxFileBytes) {
	}
}
