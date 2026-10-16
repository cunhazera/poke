package com.app.poke.application.port.in;

import java.util.Objects;

import com.app.poke.domain.model.PokedexNumber;

public record GetPokemonDetailQuery(PokedexNumber pokedexNumber) {

	public GetPokemonDetailQuery {
		Objects.requireNonNull(pokedexNumber, "pokedexNumber");
	}

	/** @throws com.app.poke.domain.exception.InvalidPokemonIdException if {@code id} is not a positive number */
	public static GetPokemonDetailQuery of(int id) {
		return new GetPokemonDetailQuery(new PokedexNumber(id));
	}
}
