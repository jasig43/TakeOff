package com.takeoff.backend.dto;

import java.time.Instant;

import com.takeoff.backend.model.Role;
import com.takeoff.backend.model.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Requests and responses used only by the administrator's user-management endpoints. */
public final class UserAdminDtos {

	private UserAdminDtos() {
	}

	/**
	 * An administrator creating an account for someone. There is no password field: the server generates a temporary
	 * one and returns it once. The phone is taken as verified (the administrator vouches for the person).
	 */
	public record CreateAccountRequest(

			@NotBlank(message = "Full name is required.")
			@Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters.")
			String fullName,

			@NotBlank(message = "Email address is required.")
			@Email(message = "Enter a valid email address.")
			@Size(max = 254, message = "Email address is too long.")
			String email,

			@NotBlank(message = "Phone number is required.")
			@Pattern(regexp = "^\\+[1-9]\\d{6,14}$",
					message = "Phone number must be in international format, for example +263771234567.")
			@Pattern(regexp = "^(?!\\+2630).*$",
					message = "Zimbabwe numbers are written without the leading 0 after +263, for example +263771234567.")
			String phoneNumber,

			@NotNull(message = "Choose a role.")
			Role role) {
	}

	public record ChangeRoleRequest(@NotNull(message = "Choose a role.") Role role) {
	}

	/** One account as an administrator sees it. Never contains the password hash. */
	public record AdminUserDto(Long id, String fullName, String email, String phoneNumber, Role role,
			boolean phoneVerified, boolean enabled, boolean mustChangePassword, Instant temporaryPasswordExpiresAt,
			Instant createdAt) {

		public static AdminUserDto from(User user) {
			return new AdminUserDto(user.getId(), user.getFullName(), user.getEmail(), user.getPhoneNumber(),
					user.getRole(), user.isPhoneVerified(), user.isEnabled(), user.isMustChangePassword(),
					user.getTemporaryPasswordExpiresAt(), user.getCreatedAt());
		}
	}

	/**
	 * A freshly issued temporary password, shown to the administrator exactly once. It is never stored in readable
	 * form and never appears in any other response or log line.
	 */
	public record IssuedCredentialDto(AdminUserDto user, String temporaryPassword, Instant temporaryPasswordExpiresAt) {

		@Override
		public String toString() {
			return "IssuedCredentialDto[user=" + user.id() + ", temporaryPassword=<redacted>]";
		}
	}
}
