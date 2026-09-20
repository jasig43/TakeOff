package com.takeoff.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import com.takeoff.backend.config.TakeoffProperties;

/**
 * Encodes OTPs for storage and compares submitted codes against stored ones.
 *
 * <p>With {@code takeoff.otp.hash-codes=true} (the default, and mandatory outside dev/test) the stored
 * value is {@code HMAC-SHA256(pepper, userId + ":" + code)} as hex, so a database leak does not reveal
 * live codes. With hashing off (dev/test only) the plain code is stored to make local debugging easy.
 *
 * <p>MVP limitation: a 6-digit code has only 10^6 possibilities, so even a peppered hash is weak against
 * an attacker who has both the database and the pepper. Short expiry and the per-token attempt limit are
 * the real protections.
 */
@Component
public class OtpCodec {

	private final boolean hashing;
	private final byte[] pepper;

	public OtpCodec(TakeoffProperties properties, Environment environment) {
		TakeoffProperties.Otp otp = properties.otp();
		this.hashing = otp.hashCodes();
		if (!hashing && !environment.acceptsProfiles(Profiles.of("dev", "test"))) {
			throw new IllegalStateException(
					"takeoff.otp.hash-codes=false is only allowed in the dev/test profiles; OTPs must be hashed elsewhere.");
		}
		if (hashing && (otp.pepper() == null || otp.pepper().isBlank())) {
			throw new IllegalStateException(
					"OTP_PEPPER must be set (a long random string) because OTP hashing is enabled.");
		}
		this.pepper = otp.pepper() == null ? new byte[0] : otp.pepper().getBytes(StandardCharsets.UTF_8);
	}

	/** Value to persist in {@code otp_tokens.code}. */
	public String encode(long userId, String code) {
		if (!hashing) {
			return code;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
			byte[] digest = mac.doFinal((userId + ":" + code).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 is not available", ex);
		}
	}

	/** Constant-time comparison of a submitted code with the stored value. */
	public boolean matches(long userId, String submittedCode, String storedValue) {
		byte[] expected = encode(userId, submittedCode).getBytes(StandardCharsets.UTF_8);
		byte[] actual = storedValue.getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(expected, actual);
	}
}
