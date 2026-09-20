package com.takeoff.backend.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An in-app notification for a user (e.g. "your application was approved"). */
@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(nullable = false, length = 40, updatable = false)
	private String type;

	@Column(nullable = false, length = 150, updatable = false)
	private String title;

	@Column(nullable = false, length = 600, updatable = false)
	private String message;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Notification() {
		// for JPA
	}

	public Notification(Long userId, String type, String title, String message, Instant createdAt) {
		this.userId = userId;
		this.type = type;
		this.title = title;
		this.message = message;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getType() {
		return type;
	}

	public String getTitle() {
		return title;
	}

	public String getMessage() {
		return message;
	}

	public boolean isRead() {
		return read;
	}

	public void markRead() {
		this.read = true;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
