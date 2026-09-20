package com.takeoff.backend.dto;

import com.takeoff.backend.model.VehicleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Step 3 of the application. */
public record VehicleRequest(

		@NotNull(message = "Vehicle type is required.")
		VehicleType vehicleType,

		@NotBlank(message = "Registration (plate) number is required.")
		@Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 \\-]{1,13}[A-Za-z0-9]$",
				message = "Enter a valid registration number (3 to 15 letters, digits, spaces or dashes).")
		String plateNumber,

		@NotBlank(message = "Vehicle make is required.")
		@Size(max = 50, message = "Vehicle make must be at most 50 characters.")
		String make,

		@NotBlank(message = "Vehicle model is required.")
		@Size(max = 50, message = "Vehicle model must be at most 50 characters.")
		String model) {
}
