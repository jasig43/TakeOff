package com.takeoff.backend.dto;

import java.time.Instant;

/**
 * Message published to RabbitMQ when an OTP must be generated. Carries the minimum needed and
 * never a password, token or the OTP itself.
 */
public record OtpEvent(String eventType, Long userId, String phoneNumber, Instant requestedAt) {

	public static final String OTP_GENERATE = "OTP_GENERATE";

	public static OtpEvent generate(Long userId, String phoneNumber, Instant requestedAt) {
		return new OtpEvent(OTP_GENERATE, userId, phoneNumber, requestedAt);
	}
}
