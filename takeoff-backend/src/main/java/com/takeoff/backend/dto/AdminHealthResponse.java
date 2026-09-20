package com.takeoff.backend.dto;

import java.time.Instant;

import com.takeoff.backend.model.Role;

public record AdminHealthResponse(String status, String message, Role role, Instant timestamp) {
}
