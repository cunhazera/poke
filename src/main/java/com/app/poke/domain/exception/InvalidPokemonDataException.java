package com.app.poke.domain.exception;

/** A local Pokémon field breaks a rule; {@link #field()} names it so clients can point at the right input. */
public class InvalidPokemonDataException extends RuntimeException {

	private final String field;

	public InvalidPokemonDataException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
