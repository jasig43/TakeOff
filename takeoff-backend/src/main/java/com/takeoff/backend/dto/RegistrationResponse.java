package com.takeoff.backend.dto;

/**
 * Safe registration result. It never contains the password, a hash, a token or the OTP itself.
 *
 * @param otpDispatched {@code false} when the account was created but the OTP event could not be
 *                      queued; the client should offer "Resend code".
 */
public record RegistrationResponse(String email, String maskedPhone, long otpExpiresInSeconds, boolean otpDispatched,
		String message) {
}
