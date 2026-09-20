package com.takeoff.backend.dto;

public record JwtResponse(String accessToken, String tokenType, long expiresInSeconds, UserSummaryDto user) {

	public static JwtResponse bearer(String accessToken, long expiresInSeconds, UserSummaryDto user) {
		return new JwtResponse(accessToken, "Bearer", expiresInSeconds, user);
	}
}
