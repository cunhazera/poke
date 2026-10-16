package com.app.poke.domain.exception;

public class InvalidPokemonIdException extends RuntimeException {

	public InvalidPokemonIdException(String message) {
		super(message);
	}
}
