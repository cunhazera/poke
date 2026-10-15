package com.app.poke.domain.exception;

public class InvalidPageRequestException extends RuntimeException {

	public InvalidPageRequestException(String message) {
		super(message);
	}
}
