package com.takeoff.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpVerifyRequest(

		/** Email address or E.164 phone number of the account being verified. */
		@NotBlank(message = "Identifier is required.")
		@Size(max = 254, message = "Identifier is too long.")
		String identifier,

		@NotBlank(message = "Verification code is required.")
		@Pattern(regexp = "^\\d{6}$", message = "The verification code must be exactly 6 digits.")
		String otp) {
}
