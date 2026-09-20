package com.takeoff.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Step 2 of the application: national ID and driver's licence. */
public record IdentityRequest(

		@NotBlank(message = "National ID number is required.")
		@Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 \\-]{4,18}[A-Za-z0-9]$",
				message = "Enter a valid national ID number (6 to 20 letters, digits, spaces or dashes).")
		String nationalId,

		@NotBlank(message = "Licence number is required.")
		@Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 /\\-]{2,28}[A-Za-z0-9]$",
				message = "Enter a valid licence number (4 to 30 letters, digits, spaces, dashes or slashes).")
		String licenceNumber,

		@NotBlank(message = "Licence class is required.")
		@Pattern(regexp = "^[A-Za-z0-9 ,\\-]{1,20}$", message = "Enter a valid licence class, for example 4.")
		String licenceClass,

		@NotNull(message = "Licence expiry date is required.")
		@Future(message = "Your licence must not have expired.")
		LocalDate licenceExpiry) {
}
