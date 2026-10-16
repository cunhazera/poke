package com.example.tasks.error;

/** The request body refers to something that doesn't exist, e.g. assigning a task to an unknown user (422). */
public class UnknownReferenceException extends RuntimeException {

	private final String field;

	public UnknownReferenceException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
