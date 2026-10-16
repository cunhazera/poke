package com.app.poke.domain.model;

import com.app.poke.domain.exception.InvalidPokemonDataException;

/** A Pokémon skill; hidden abilities are only obtainable through special means. */
public record Ability(String name, boolean hidden) {

	public Ability {
		if (name == null || name.isBlank()) {
			throw new InvalidPokemonDataException("abilities", "ability names must not be blank");
		}
	}
}
