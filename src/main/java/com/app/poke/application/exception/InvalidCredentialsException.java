package com.app.poke.application.exception;

/** Deliberately says nothing about which part was wrong, so it can't be used to discover usernames. */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid username, email or password");
	}
}
