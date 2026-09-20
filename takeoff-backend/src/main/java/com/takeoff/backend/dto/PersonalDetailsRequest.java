package com.takeoff.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Step 1 of the application. The minimum age (18) is checked in the service against the clock. */
public record PersonalDetailsRequest(

		@NotNull(message = "Date of birth is required.")
		@Past(message = "Date of birth must be in the past.")
		LocalDate dateOfBirth,

		@NotBlank(message = "Address is required.")
		@Size(max = 200, message = "Address must be at most 200 characters.")
		String addressLine,

		@NotBlank(message = "City or town is required.")
		@Size(max = 100, message = "City or town must be at most 100 characters.")
		String city,

		@NotBlank(message = "Emergency contact name is required.")
		@Size(min = 2, max = 100, message = "Emergency contact name must be between 2 and 100 characters.")
		String emergencyContactName,

		@NotBlank(message = "Emergency contact phone is required.")
		@Pattern(regexp = "^\\+[1-9]\\d{6,14}$",
				message = "Phone number must be in international format, for example +263771234567.")
		@Pattern(regexp = "^(?!\\+2630).*$",
				message = "Zimbabwe numbers are written without the leading 0 after +263, for example +263771234567.")
		String emergencyContactPhone) {
}
