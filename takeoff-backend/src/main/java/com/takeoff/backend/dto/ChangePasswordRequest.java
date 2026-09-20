package com.takeoff.backend.dto;

import com.takeoff.backend.validation.CompliantPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Change of the signed-in user's own password. The new one must satisfy the same policy as registration. */
public record ChangePasswordRequest(

		@NotBlank(message = "Enter your current password.")
		String currentPassword,

		@NotNull(message = "Enter a new password.")
		@CompliantPassword
		String newPassword) {

	/** A record prints all its components by default; passwords must never reach a log line. */
	@Override
	public String toString() {
		return "ChangePasswordRequest[currentPassword=<redacted>, newPassword=<redacted>]";
	}
}
