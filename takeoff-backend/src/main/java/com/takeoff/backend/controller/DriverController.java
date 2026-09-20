package com.takeoff.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.takeoff.backend.dto.DriverProfileDto;
import com.takeoff.backend.security.TakeoffUserDetails;
import com.takeoff.backend.service.DriverService;

/** Applicant-only endpoints; {@code SecurityConfig} requires ROLE_APPLICANT_DRIVER for {@code /api/v1/drivers/**}. */
@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

	private final DriverService driverService;

	public DriverController(DriverService driverService) {
		this.driverService = driverService;
	}

	@GetMapping("/profile")
	public DriverProfileDto profile(@AuthenticationPrincipal TakeoffUserDetails principal) {
		return driverService.getProfile(principal.getId());
	}
}
