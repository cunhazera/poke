package com.app.poke.domain.exception;

public class LocalPokemonNotFoundException extends RuntimeException {

	public LocalPokemonNotFoundException(long id) {
		super("Local Pokémon %d not found".formatted(id));
	}
}
