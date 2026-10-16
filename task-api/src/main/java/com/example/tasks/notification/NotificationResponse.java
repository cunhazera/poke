package com.example.tasks.notification;

import java.time.Instant;

import com.example.tasks.user.UserSummary;

public record NotificationResponse(long id, long taskId, UserSummary recipient, String message, Instant sentAt) {

	static NotificationResponse of(Notification notification) {
		return new NotificationResponse(notification.getId(), notification.getTask().getId(),
				UserSummary.of(notification.getRecipient()), notification.getMessage(), notification.getSentAt());
	}
}
