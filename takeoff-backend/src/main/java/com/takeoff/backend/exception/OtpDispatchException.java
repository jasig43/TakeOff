package com.takeoff.backend.exception;

import org.springframework.http.HttpStatus;

/** The OTP event could not be published to RabbitMQ (broker down, serialization failure, ...). */
public class OtpDispatchException extends ApiException {

	public OtpDispatchException(Throwable cause) {
		super(HttpStatus.SERVICE_UNAVAILABLE, "OTP_DISPATCH_FAILED",
				"We couldn't send a verification code right now. Please try again shortly.", cause);
	}
}
