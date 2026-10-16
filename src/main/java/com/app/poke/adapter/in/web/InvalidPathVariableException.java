package com.app.poke.adapter.in.web;

/** A path variable that isn't a valid id; a request shape problem, so it never reaches a use case. */
class InvalidPathVariableException extends RuntimeException {

	InvalidPathVariableException(String name, String value) {
		super("'%s' is not a valid %s: use a positive whole number".formatted(value, name));
	}
}
