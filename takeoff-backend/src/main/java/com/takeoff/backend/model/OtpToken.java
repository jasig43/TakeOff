package com.takeoff.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A one-time verification code. {@link #getCode()} holds the plain 6-digit code only when
 * {@code takeoff.otp.hash-codes=false} (dev/test); otherwise it holds an HMAC-SHA256 hex digest.
 */
@Entity
@Table(name = "otp_tokens")
public class OtpToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 64)
	private String code;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private boolean consumed;

	/** Number of wrong guesses against this token; the token is burned after the configured maximum. */
	@Column(nullable = false)
	private int attempts;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected OtpToken() {
		// for JPA
	}

	public OtpToken(Long userId, String code, Instant expiresAt, Instant createdAt) {
		this.userId = userId;
		this.code = code;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getCode() {
		return code;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public boolean isConsumed() {
		return consumed;
	}

	public void setConsumed(boolean consumed) {
		this.consumed = consumed;
	}

	public int getAttempts() {
		return attempts;
	}

	public void setAttempts(int attempts) {
		this.attempts = attempts;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
