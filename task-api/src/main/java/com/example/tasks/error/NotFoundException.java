package com.example.tasks.error;

/** The resource in the request path doesn't exist (404). */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String what, long id) {
		super("%s %d not found".formatted(what, id));
	}
}
