package com.app.poke.domain.exception;

/** Usernames are unique regardless of case. */
public class UsernameTakenException extends RuntimeException {

	public UsernameTakenException(String username) {
		super("Username '%s' is already taken".formatted(username));
	}
}
