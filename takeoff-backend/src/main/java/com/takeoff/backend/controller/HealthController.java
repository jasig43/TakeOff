package com.takeoff.backend.controller;

import java.time.Clock;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public liveness probe for the host's health check ({@code GET /api/v1/health}). It says nothing about the
 * application's data and touches no other system, so it can be called freely and answers even while the database or
 * queue is having a bad moment.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

	private final Clock clock;

	public HealthController(Clock clock) {
		this.clock = clock;
	}

	@GetMapping
	public Map<String, Object> health() {
		return Map.of("status", "UP", "service", "takeoff-backend", "timestamp", clock.instant().toString());
	}
}
