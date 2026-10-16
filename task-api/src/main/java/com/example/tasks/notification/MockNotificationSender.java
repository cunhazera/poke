package com.example.tasks.notification;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.tasks.task.Task;
import com.example.tasks.user.AppUser;

/**
 * Not a real channel: it logs the notification and records it in the {@code notification} table, which
 * {@code GET /api/notifications} shows. A real one (e-mail, chat) would implement {@link NotificationSender} instead.
 */
@Component
class MockNotificationSender implements NotificationSender {

	private static final Logger log = LoggerFactory.getLogger(MockNotificationSender.class);

	private final NotificationRepository notifications;
	private final Clock clock;

	MockNotificationSender(NotificationRepository notifications, Clock clock) {
		this.notifications = notifications;
		this.clock = clock;
	}

	@Override
	public void send(Task task, AppUser recipient, String message) {
		notifications.save(new Notification(task, recipient, message, clock.instant()));
		log.info("[mock notification] to {} <{}>: {}", recipient.getUsername(), recipient.getEmail(), message);
	}
}
