package com.takeoff.backend.model;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "full_name", nullable = false, length = 100)
	private String fullName;

	/** Stored lower-cased; unique. */
	@Column(nullable = false, unique = true, length = 254)
	private String email;

	/** E.164 format; unique. */
	@Column(name = "phone_number", nullable = false, unique = true, length = 20)
	private String phoneNumber;

	/** BCrypt hash. The plain-text password is never stored, logged or returned. */
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	// Forced to VARCHAR: Hibernate would otherwise use a native ENUM column type on MySQL/H2, which
	// does not match the portable VARCHAR column created by the Flyway migration.
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private Role role;

	@Column(name = "phone_verified", nullable = false)
	private boolean phoneVerified;

	@Column(nullable = false)
	private boolean enabled = true;

	/** True while the password is a temporary one issued by an administrator; the person must replace it first. */
	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword;

	/** When the temporary password stops working; null when there is no temporary password. */
	@Column(name = "temporary_password_expires_at")
	private Instant temporaryPasswordExpiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
		// for JPA
	}

	public User(String fullName, String email, String phoneNumber, String passwordHash, Role role) {
		this.fullName = fullName;
		this.email = email;
		this.phoneNumber = phoneNumber;
		this.passwordHash = passwordHash;
		this.role = role;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public String getEmail() {
		return email;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	/**
	 * The person chose their own password. Callers pass an already-encoded hash, never a plain-text password. Clears
	 * any temporary-password state.
	 */
	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
		this.mustChangePassword = false;
		this.temporaryPasswordExpiresAt = null;
	}

	/**
	 * An administrator issued a temporary password (already hashed). Until the person replaces it they can do nothing
	 * else, and it stops working at {@code expiresAt}.
	 */
	public void issueTemporaryPassword(String passwordHash, Instant expiresAt) {
		this.passwordHash = passwordHash;
		this.mustChangePassword = true;
		this.temporaryPasswordExpiresAt = expiresAt;
	}

	/** True when the password is a temporary one whose time has run out. */
	public boolean temporaryPasswordExpired(Instant now) {
		return mustChangePassword && temporaryPasswordExpiresAt != null && !temporaryPasswordExpiresAt.isAfter(now);
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	public Instant getTemporaryPasswordExpiresAt() {
		return temporaryPasswordExpiresAt;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

	public boolean isPhoneVerified() {
		return phoneVerified;
	}

	public void setPhoneVerified(boolean phoneVerified) {
		this.phoneVerified = phoneVerified;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
