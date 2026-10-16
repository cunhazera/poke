package com.app.poke.domain.model;

import java.math.BigDecimal;

import com.app.poke.domain.exception.InvalidPokemonDataException;

/** A Pokémon's height. PokeAPI reports decimetres (1 dm = 0.1 m). */
public record Height(int decimetres) {

	/** Eternamax Eternatus, the tallest form in the games, is 100 m. */
	public static final BigDecimal MAX_METRES = new BigDecimal("100.0");

	public Height {
		if (decimetres < 0) {
			throw new InvalidPokemonDataException("heightM", "height must be >= 0 dm, was " + decimetres);
		}
	}

	public static Height ofDecimetres(int decimetres) {
		return new Height(decimetres);
	}

	/** A height given in metres, as the API speaks: 0 to 100 m, with at most one decimal (the store keeps decimetres). */
	public static Height ofMetres(BigDecimal metres) {
		return new Height(Measures.tenths("heightM", metres, MAX_METRES));
	}

	public double metres() {
		return decimetres / 10.0;
	}
}
