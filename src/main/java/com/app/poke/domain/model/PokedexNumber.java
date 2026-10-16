package com.app.poke.domain.model;

import com.app.poke.domain.exception.InvalidPokemonIdException;

/** A national Pokédex number. Whether a species exists for it is the catalog's call, not this type's. */
public record PokedexNumber(int value) {

	public PokedexNumber {
		if (value < 1) {
			throw new InvalidPokemonIdException("Pokémon id must be a positive Pokédex number, was " + value);
		}
	}
}
