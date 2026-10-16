package com.example.tasks.notification;

import com.example.tasks.task.Task;
import com.example.tasks.user.AppUser;

/** Sends a notification about a task to a user. The only implementation is a mock ({@link MockNotificationSender}). */
public interface NotificationSender {

	void send(Task task, AppUser recipient, String message);
}
