package com.example.tasks.task;

import java.time.Instant;

import com.example.tasks.user.UserSummary;

/** One action on a task: for a status change, from and to; for an owner change, the previous and new owner. */
public record HistoryResponse(long id, TaskAction action, TaskStatus fromStatus, TaskStatus toStatus,
		UserSummary fromOwner, UserSummary toOwner, Instant occurredAt) {

	static HistoryResponse of(TaskHistory entry) {
		return new HistoryResponse(entry.getId(), entry.getAction(), entry.getFromStatus(), entry.getToStatus(),
				UserSummary.of(entry.getFromOwner()), UserSummary.of(entry.getToOwner()), entry.getOccurredAt());
	}
}
