package com.takeoff.backend.dto;

import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;

/** Safe subset of a user. Never contains the password hash. */
public record UserSummaryDto(Long id, String fullName, String email, String phoneNumber, Role role,
		boolean phoneVerified) {

	public static UserSummaryDto from(User user) {
		return new UserSummaryDto(user.getId(), user.getFullName(), user.getEmail(), user.getPhoneNumber(),
				user.getRole(), user.isPhoneVerified());
	}
}
