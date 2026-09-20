package com.takeoff.backend.exception;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.takeoff.backend.exception.ApiErrorResponse.FieldViolation;

/** A validation failure that needs the business context of a service (e.g. "you must be at least 18"), reported per field. */
public class FieldValidationException extends ApiException {

	private final List<FieldViolation> violations;

	public FieldValidationException(String field, String message) {
		super(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
		this.violations = List.of(new FieldViolation(field, message));
	}

	public List<FieldViolation> getViolations() {
		return violations;
	}
}
