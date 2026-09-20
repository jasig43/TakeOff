package com.takeoff.backend.dto;

import java.time.Instant;
import java.util.List;

import com.takeoff.backend.model.Notification;

public record NotificationDto(Long id, String type, String title, String message, boolean read, Instant createdAt) {

	public static NotificationDto from(Notification notification) {
		return new NotificationDto(notification.getId(), notification.getType(), notification.getTitle(),
				notification.getMessage(), notification.isRead(), notification.getCreatedAt());
	}

	/** The list plus the unread count, so the sidebar badge needs a single request. */
	public record Inbox(List<NotificationDto> items, long unreadCount) {
	}
}
