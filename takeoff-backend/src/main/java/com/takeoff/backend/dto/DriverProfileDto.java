package com.takeoff.backend.dto;

import java.time.Instant;

import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;

public record DriverProfileDto(Long id, String fullName, String email, String phoneNumber, boolean phoneVerified,
		Role role, Instant createdAt) {

	public static DriverProfileDto from(User user) {
		return new DriverProfileDto(user.getId(), user.getFullName(), user.getEmail(), user.getPhoneNumber(),
				user.isPhoneVerified(), user.getRole(), user.getCreatedAt());
	}
}
