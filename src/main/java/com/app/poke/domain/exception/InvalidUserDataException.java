package com.app.poke.domain.exception;

/** A user field breaks a rule; {@link #field()} names it so clients can point at the right input. */
public class InvalidUserDataException extends RuntimeException {

	private final String field;

	public InvalidUserDataException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
