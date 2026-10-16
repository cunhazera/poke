package com.app.poke.domain.exception;

public class PokemonNotFoundException extends RuntimeException {

	private final int pokemonId;

	public PokemonNotFoundException(int pokemonId) {
		super("Pokemon %d not found".formatted(pokemonId));
		this.pokemonId = pokemonId;
	}

	public int pokemonId() {
		return pokemonId;
	}
}
