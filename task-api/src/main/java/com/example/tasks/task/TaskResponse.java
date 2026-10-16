package com.example.tasks.task;

import java.time.Instant;

import com.example.tasks.user.UserSummary;

/**
 * @param owner null until someone is assigned
 * @param overdue true once an open task's due date is reached
 * @param warning the overdue warning shown on the board; null when the task isn't overdue
 */
public record TaskResponse(long id, String title, String description, TaskStatus status, Instant dueDate,
		UserSummary owner, boolean overdue, String warning, Instant createdAt, Instant updatedAt) {

	static TaskResponse of(Task task, Instant now) {
		return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.getStatus(), task.getDueDate(),
				UserSummary.of(task.getOwner()), task.isOverdue(now), task.overdueWarning(now), task.getCreatedAt(),
				task.getUpdatedAt());
	}
}
