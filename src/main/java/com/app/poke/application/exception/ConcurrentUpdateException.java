package com.app.poke.application.exception;

/** Someone else changed the same record between our read and our write; retrying with fresh data is safe. */
public class ConcurrentUpdateException extends RuntimeException {

	public ConcurrentUpdateException(String message) {
		super(message);
	}
}
