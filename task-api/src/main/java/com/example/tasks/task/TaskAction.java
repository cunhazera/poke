package com.example.tasks.task;

/** What happened to a task, as recorded in its history. */
public enum TaskAction {
	CREATED,
	DETAILS_UPDATED,
	STATUS_CHANGED,
	OWNER_CHANGED,
	OVERDUE_NOTIFIED
}
