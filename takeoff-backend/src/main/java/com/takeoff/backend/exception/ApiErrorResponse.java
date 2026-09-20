package com.takeoff.backend.exception;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;

/** The single error body shape returned by every failing endpoint, including 401 and 403. */
public record ApiErrorResponse(Instant timestamp, int status, String error, String code, String message,
		String path, List<FieldViolation> fieldErrors) {

	public record FieldViolation(String field, String message) {
	}

	public static ApiErrorResponse of(HttpStatus status, String code, String message, String path) {
		return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path,
				List.of());
	}

	public static ApiErrorResponse of(HttpStatus status, String code, String message, String path,
			List<FieldViolation> fieldErrors) {
		return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path,
				fieldErrors);
	}
}
