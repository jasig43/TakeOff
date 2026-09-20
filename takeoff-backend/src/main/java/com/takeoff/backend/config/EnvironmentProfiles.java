package com.takeoff.backend.config;

import org.springframework.core.env.Profiles;

/**
 * The profiles in which development conveniences are allowed: one-time codes printed to the log, un-hashed OTPs, the
 * fixed evaluator test number, and the optional seeded test driver.
 *
 * <ul>
 *   <li>{@code dev}: local development, with throw-away built-in secrets.</li>
 *   <li>{@code test}: the automated tests.</li>
 *   <li>{@code demo}: the hosted demo. It gets the same conveniences (so a reviewer can read the OTP from the log and
 *       use the test number) but, unlike {@code dev}, has <b>no</b> built-in secrets: the JWT secret, database and
 *       queue settings must all come from the environment.</li>
 * </ul>
 *
 * Anything that would be dangerous in production (the built-in JWT secret, in particular) stays limited to
 * {@code dev} and {@code test}.
 */
public final class EnvironmentProfiles {

	public static final Profiles RELAXED = Profiles.of("dev", "demo", "test");

	private EnvironmentProfiles() {
	}
}
