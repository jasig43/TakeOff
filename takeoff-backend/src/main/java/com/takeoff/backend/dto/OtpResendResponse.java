package com.takeoff.backend.dto;

public record OtpResendResponse(long otpExpiresInSeconds, boolean otpDispatched, String message) {
}
