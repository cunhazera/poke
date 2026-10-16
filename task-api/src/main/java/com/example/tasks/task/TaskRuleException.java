package com.example.tasks.task;

/** The request is well-formed, but the task's current state doesn't allow it (e.g. Todo → Done). */
public class TaskRuleException extends RuntimeException {

	public TaskRuleException(String message) {
		super(message);
	}
}
