package com.app.poke.domain.model;

import java.math.BigDecimal;

import com.app.poke.domain.exception.InvalidPokemonDataException;

/** A Pokémon's mass. PokeAPI reports hectograms (1 hg = 0.1 kg). */
public record Weight(int hectograms) {

	/** The heaviest Pokémon in the games weigh under 1000 kg; this leaves room without allowing nonsense. */
	public static final BigDecimal MAX_KILOGRAMS = new BigDecimal("10000.0");

	public Weight {
		if (hectograms < 0) {
			throw new InvalidPokemonDataException("weightKg", "weight must be >= 0 hg, was " + hectograms);
		}
	}

	public static Weight ofHectograms(int hectograms) {
		return new Weight(hectograms);
	}

	/** A mass given in kilograms, as the API speaks: 0 to 10000 kg, with at most one decimal (the store keeps hectograms). */
	public static Weight ofKilograms(BigDecimal kilograms) {
		return new Weight(Measures.tenths("weightKg", kilograms, MAX_KILOGRAMS));
	}

	public double kilograms() {
		return hectograms / 10.0;
	}
}
