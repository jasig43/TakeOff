package com.takeoff.backend.dto;

import com.takeoff.backend.model.ApplicationStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An administrator's decision. {@code status} must be APPROVED or REJECTED; a note is mandatory for REJECTED so the driver
 * learns why (both rules are enforced in the service).
 */
public record DecisionRequest(

		@NotNull(message = "A decision is required.")
		ApplicationStatus status,

		@Size(max = 500, message = "The note must be at most 500 characters.")
		String note) {
}
