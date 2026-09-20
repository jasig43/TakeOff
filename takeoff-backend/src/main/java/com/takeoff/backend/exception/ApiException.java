package com.takeoff.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * A failure that maps directly to an HTTP response. {@code code} is a stable machine-readable
 * identifier the frontend can branch on; {@code message} is safe to show to end users.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public ApiException(HttpStatus status, String code, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}
}
