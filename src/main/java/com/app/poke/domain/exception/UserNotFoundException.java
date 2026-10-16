package com.app.poke.domain.exception;

public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException(long userId) {
		super("User %d not found".formatted(userId));
	}
}
