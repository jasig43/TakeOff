package com.takeoff.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code takeoff.*} configuration. Values come from application.yml and are
 * overridable through environment variables (see .env.example).
 */
@ConfigurationProperties(prefix = "takeoff")
public record TakeoffProperties(Jwt jwt, Cors cors, Otp otp, Rabbit rabbitmq, Admin admin) {

	public record Jwt(String secret, long expirationMinutes, String issuer) {
	}

	public record Cors(List<String> allowedOrigins) {
	}

	public record Otp(long expirationSeconds, long resendCooldownSeconds, int maxAttempts, boolean hashCodes,
			String pepper, boolean logToConsole, TestBypass testBypass) {
	}

	/** Evaluator shortcut: {@code phoneNumber} always receives {@code code}. Refused outside dev/test. */
	public record TestBypass(boolean enabled, String phoneNumber, String code) {
	}

	public record Rabbit(String exchange, String queue, String routingKey) {
	}

	public record Admin(Seed seed) {
	}

	public record Seed(boolean enabled, String email, String password, String phoneNumber, String fullName) {
	}
}
