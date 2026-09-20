package com.takeoff.backend.service;

import java.time.Clock;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.takeoff.backend.dto.NotificationDto;
import com.takeoff.backend.exception.ApiException;
import com.takeoff.backend.model.Notification;
import com.takeoff.backend.repository.NotificationRepository;

/** In-app notifications. Every method is scoped to one user id taken from the authenticated principal. */
@Service
public class NotificationService {

	private final NotificationRepository notifications;
	private final Clock clock;

	public NotificationService(NotificationRepository notifications, Clock clock) {
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional
	public void create(Long userId, String type, String title, String message) {
		notifications.save(new Notification(userId, type, title, message, clock.instant()));
	}

	@Transactional(readOnly = true)
	public NotificationDto.Inbox inbox(Long userId) {
		List<NotificationDto> items = notifications.findTop50ByUserIdOrderByIdDesc(userId)
			.stream()
			.map(NotificationDto::from)
			.toList();
		return new NotificationDto.Inbox(items, notifications.countByUserIdAndReadFalse(userId));
	}

	@Transactional
	public void markRead(Long userId, Long notificationId) {
		Notification notification = notifications.findByIdAndUserId(notificationId, userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found."));
		notification.markRead();
	}

	@Transactional
	public void markAllRead(Long userId) {
		notifications.markAllRead(userId);
	}
}
