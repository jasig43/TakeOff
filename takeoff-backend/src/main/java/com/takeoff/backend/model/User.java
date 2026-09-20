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

	public Role getRole() {
		return role;
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
