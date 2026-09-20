package com.takeoff.backend.dto;

import java.time.Instant;

/**
 * Published to RabbitMQ after an administrator decides an application; the notification listener texts the driver.
 * Contains identifiers and the outcome only: no personal details, no reason text, no secrets.
 */
public record DecisionEvent(String eventType, Long applicationId, Long userId, String status, String referenceId,
		Instant requestedAt) {

	public static final String APPLICATION_DECIDED = "APPLICATION_DECIDED";

	public static DecisionEvent of(Long applicationId, Long userId, String status, String referenceId, Instant at) {
		return new DecisionEvent(APPLICATION_DECIDED, applicationId, userId, status, referenceId, at);
	}
}
