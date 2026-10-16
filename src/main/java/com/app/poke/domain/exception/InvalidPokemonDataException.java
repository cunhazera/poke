package com.app.poke.domain.exception;

/**
 * A Pokémon field breaks a rule; {@link #field()} names it (as the API spells it) so clients can point at the right
 * input. It is an {@link IllegalArgumentException}, so data from the catalog that breaks a rule is still treated as
 * an upstream failure (503) where catalog data is mapped.
 */
public class InvalidPokemonDataException extends IllegalArgumentException {

	private final String field;

	public InvalidPokemonDataException(String field, String message) {
		super(message);
		this.field = field;
	}

	public String field() {
		return field;
	}
}
