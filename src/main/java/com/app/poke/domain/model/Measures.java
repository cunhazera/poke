package com.app.poke.domain.model;

import java.math.BigDecimal;

import com.app.poke.domain.exception.InvalidPokemonDataException;

/** Conversion of API measurements (m, kg) to PokeAPI's tenths (dm, hg) without rounding. */
final class Measures {

	private Measures() {
	}

	static int tenths(String field, BigDecimal value, BigDecimal max) {
		if (value == null) {
			throw new InvalidPokemonDataException(field, field + " is required");
		}
		if (value.signum() < 0 || value.compareTo(max) > 0 || value.stripTrailingZeros().scale() > 1) {
			throw new InvalidPokemonDataException(field,
					"%s must be within 0..%s with at most one decimal, was %s".formatted(field, max, value.toPlainString()));
		}
		return value.movePointRight(1).intValueExact();
	}
}
