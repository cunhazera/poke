package com.app.poke.domain.model;

/** A Pokémon's mass. PokeAPI reports hectograms (1 hg = 0.1 kg). */
public record Weight(int hectograms) {

	public Weight {
		if (hectograms < 0) {
			throw new IllegalArgumentException("weight must be >= 0 hg, was " + hectograms);
		}
	}

	public static Weight ofHectograms(int hectograms) {
		return new Weight(hectograms);
	}

	public double kilograms() {
		return hectograms / 10.0;
	}
}
