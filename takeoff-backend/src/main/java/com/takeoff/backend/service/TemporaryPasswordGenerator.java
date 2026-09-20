package com.takeoff.backend.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Generates the one-time temporary passwords administrators hand out. They come from a cryptographically secure
 * generator, are long enough and varied enough to satisfy the password policy on their own (15+ characters with an
 * uppercase letter and a special character), and avoid characters that are easy to misread when copied by hand
 * (I, l, 1, O, 0) or awkward to type.
 */
@Component
public class TemporaryPasswordGenerator {

	static final int LENGTH = 18;

	private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
	private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
	private static final String DIGITS = "23456789";
	private static final String SPECIAL = "!@#$%&*-_=+?";
	private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		char[] password = new char[LENGTH];
		// One of each class first, so the policy is always met; the rest come from the full alphabet.
		password[0] = pick(UPPER);
		password[1] = pick(LOWER);
		password[2] = pick(DIGITS);
		password[3] = pick(SPECIAL);
		for (int i = 4; i < LENGTH; i++) {
			password[i] = pick(ALL);
		}
		// Fisher-Yates shuffle so the guaranteed characters are not always in the first four places.
		for (int i = LENGTH - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			char swap = password[i];
			password[i] = password[j];
			password[j] = swap;
		}
		return new String(password);
	}

	private char pick(String alphabet) {
		return alphabet.charAt(random.nextInt(alphabet.length()));
	}
}
