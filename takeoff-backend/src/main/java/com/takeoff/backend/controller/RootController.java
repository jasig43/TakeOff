package com.takeoff.backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What someone sees when they open the API's address in a browser ({@code GET /}): a short statement that it is
 * running, not a "sign in" error. The website is a separate application, so this only points people at it.
 */
@RestController
public class RootController {

	@GetMapping("/")
	public Map<String, Object> root() {
		return Map.of("service", "takeoff-backend", "status", "UP",
				"message", "The TakeOFF API is running. Use the TakeOFF website to sign in.", "health", "/api/v1/health");
	}
}
