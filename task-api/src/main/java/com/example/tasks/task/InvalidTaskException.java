package com.example.tasks.task;

import java.util.Map;

/** The data for a task breaks a rule (a missing title, a due date in the past…); every broken rule, by field. */
public class InvalidTaskException extends RuntimeException {

	private final Map<String, String> errors;

	public InvalidTaskException(Map<String, String> errors) {
		super("Invalid task: " + errors);
		this.errors = Map.copyOf(errors);
	}

	public Map<String, String> errors() {
		return errors;
	}
}
