package com.takeoff.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login payload. The password policy is intentionally NOT applied here: it is enforced when a
 * password is created, and login must never reveal which rule a wrong guess would have broken.
 */
public record LoginRequest(

		@NotBlank(message = "Email address is required.")
		@Size(max = 254, message = "Email address is too long.")
		String email,

		@NotBlank(message = "Password is required.")
		@Size(max = 256, message = "Password is too long.")
		String password) {
}
