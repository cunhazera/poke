package com.app.poke.domain.model;

import java.math.BigDecimal;

import com.app.poke.domain.exception.InvalidPokemonDataException;

/** A point on Earth in decimal degrees, with at most 6 decimals (about 11 cm), as the schema stores it. */
public record Coordinates(BigDecimal latitude, BigDecimal longitude) {

	public static final int MAX_DECIMALS = 6;
	private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
	private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

	public Coordinates {
		requireWithin("latitude", latitude, MAX_LATITUDE);
		requireWithin("longitude", longitude, MAX_LONGITUDE);
	}

	private static void requireWithin(String half, BigDecimal value, BigDecimal limit) {
		if (value == null) {
			throw new InvalidPokemonDataException("coordinates", "Coordinates need both a latitude and a longitude");
		}
		if (value.abs().compareTo(limit) > 0 || value.stripTrailingZeros().scale() > MAX_DECIMALS) {
			throw new InvalidPokemonDataException("coordinates",
					"%s must be within ±%s with at most %d decimals".formatted(half, limit, MAX_DECIMALS));
		}
	}
}
