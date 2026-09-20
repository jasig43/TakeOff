package com.takeoff.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendOtpRequest(

		/** Email address or E.164 phone number of the account that needs a new code. */
		@NotBlank(message = "Identifier is required.")
		@Size(max = 254, message = "Identifier is too long.")
		String identifier) {
}
