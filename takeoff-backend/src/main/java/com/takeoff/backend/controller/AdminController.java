package com.takeoff.backend.controller;

import java.time.Clock;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.takeoff.backend.dto.AdminHealthResponse;
import com.takeoff.backend.model.Role;

/**
 * Admin-only endpoints; {@code SecurityConfig} requires ROLE_LOGISTICS_ADMIN for {@code /api/v1/admin/**}.
 * The health probe exists to demonstrate RBAC end to end.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

	private final Clock clock;

	public AdminController(Clock clock) {
		this.clock = clock;
	}

	@GetMapping("/health")
	public AdminHealthResponse health() {
		return new AdminHealthResponse("UP", "Admin access confirmed: the server verified your LOGISTICS_ADMIN role.",
				Role.LOGISTICS_ADMIN, clock.instant());
	}
}
