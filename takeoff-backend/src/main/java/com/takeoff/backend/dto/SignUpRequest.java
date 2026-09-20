package com.takeoff.backend.dto;

import com.takeoff.backend.validation.CompliantPassword;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public registration payload. Deliberately has no {@code role} field: every public registration
 * becomes an APPLICANT_DRIVER, and any extra "role" property sent by a client is ignored.
 */
public record SignUpRequest(

		@NotBlank(message = "Full name is required.")
		@Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters.")
		String fullName,

		@NotBlank(message = "Email address is required.")
		@Email(message = "Enter a valid email address.")
		@Size(max = 254, message = "Email address is too long.")
		String email,

		@NotBlank(message = "Phone number is required.")
		@Pattern(regexp = "^\\+[1-9]\\d{6,14}$",
				message = "Phone number must be in international format, for example +15550199.")
		String phoneNumber,

		@NotNull(message = "Password is required.")
		@CompliantPassword
		String password,

		@AssertTrue(message = "You must accept the terms to create an account.")
		boolean termsAccepted) {
}
