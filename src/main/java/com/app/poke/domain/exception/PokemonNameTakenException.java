package com.app.poke.domain.exception;

/** Another local Pokémon already has this name (names are unique regardless of case). */
public class PokemonNameTakenException extends RuntimeException {

	public PokemonNameTakenException(String name) {
		super("A local Pokémon named '%s' already exists".formatted(name));
	}
}
