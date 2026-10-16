package com.example.tasks.notification;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Positive;

/** The mock channel's outbox: what would have been sent, newest first. */
@RestController
@RequestMapping("/api/notifications")
class NotificationController {

	private final NotificationRepository notifications;

	NotificationController(NotificationRepository notifications) {
		this.notifications = notifications;
	}

	@GetMapping
	List<NotificationResponse> list(@RequestParam(required = false) @Positive Long recipientId) {
		var found = recipientId == null ? notifications.findAllByOrderBySentAtDescIdDesc()
				: notifications.findByRecipientIdOrderBySentAtDescIdDesc(recipientId);
		return found.stream().map(NotificationResponse::of).toList();
	}
}
