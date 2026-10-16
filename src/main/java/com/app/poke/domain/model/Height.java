package com.app.poke.domain.model;

/** A Pokémon's height. PokeAPI reports decimetres (1 dm = 0.1 m). */
public record Height(int decimetres) {

	public Height {
		if (decimetres < 0) {
			throw new IllegalArgumentException("height must be >= 0 dm, was " + decimetres);
		}
	}

	public static Height ofDecimetres(int decimetres) {
		return new Height(decimetres);
	}

	public double metres() {
		return decimetres / 10.0;
	}
}
