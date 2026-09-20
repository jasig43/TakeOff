package com.takeoff.backend.validation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for the password rules. The frontend mirrors these rules for instant
 * feedback (takeoff-frontend/src/utils/passwordValidation.ts); this class is authoritative.
 * <ul>
 *   <li>at least {@value #MIN_LENGTH} characters</li>
 *   <li>at least one uppercase letter A-Z</li>
 *   <li>at least one special character from {@link #SPECIAL_CHARACTERS}</li>
 *   <li>at most {@value #MAX_BYTES} bytes (UTF-8): the BCrypt input limit, enforced explicitly so that
 *       an over-long password is rejected with a clear message rather than silently truncated</li>
 * </ul>
 */
public final class PasswordPolicy {

	public static final int MIN_LENGTH = 15;
	public static final int MAX_BYTES = 72;
	public static final String SPECIAL_CHARACTERS = "!@#$%^&*()_+-=[]{}|;:,.<>?";

	private PasswordPolicy() {
	}

	/** Returns a human-readable message for every rule the password breaks; empty when compliant. */
	public static List<String> violations(String password) {
		List<String> violations = new ArrayList<>();
		String value = password == null ? "" : password;

		if (value.length() < MIN_LENGTH) {
			violations.add("Password must be at least " + MIN_LENGTH + " characters long.");
		}
		if (!hasUppercaseLetter(value)) {
			violations.add("Password must contain at least one uppercase letter (A-Z).");
		}
		if (!hasSpecialCharacter(value)) {
			violations.add("Password must contain at least one special character (" + SPECIAL_CHARACTERS + ").");
		}
		if (exceedsMaxBytes(value)) {
			violations.add("Password must be at most " + MAX_BYTES + " bytes long.");
		}
		return violations;
	}

	public static boolean isCompliant(String password) {
		return violations(password).isEmpty();
	}

	public static boolean hasUppercaseLetter(String value) {
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c >= 'A' && c <= 'Z') {
				return true;
			}
		}
		return false;
	}

	public static boolean hasSpecialCharacter(String value) {
		for (int i = 0; i < value.length(); i++) {
			if (SPECIAL_CHARACTERS.indexOf(value.charAt(i)) >= 0) {
				return true;
			}
		}
		return false;
	}

	public static boolean exceedsMaxBytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
	}
}
