package com.app.poke.domain.exception;

/** Email addresses are unique regardless of case. */
public class EmailTakenException extends RuntimeException {

	public EmailTakenException() {
		super("This email address is already registered");
	}
}
