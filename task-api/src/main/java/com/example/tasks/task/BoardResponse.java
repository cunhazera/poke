package com.example.tasks.task;

import java.time.Instant;
import java.util.List;

/** The board: one column per status, in order; overdue tasks carry a warning. */
public record BoardResponse(Instant generatedAt, long overdueCount, List<Column> columns) {

	public record Column(TaskStatus status, String title, int count, List<TaskResponse> tasks) {
	}
}
