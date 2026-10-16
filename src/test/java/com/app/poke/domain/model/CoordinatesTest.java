package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.app.poke.domain.exception.InvalidPokemonDataException;

class CoordinatesTest {

	private static Coordinates at(String latitude, String longitude) {
		return new Coordinates(new BigDecimal(latitude), new BigDecimal(longitude));
	}

	@ParameterizedTest(name = "({0}, {1})")
	@CsvSource({ "0, 0", "90, 180", "-90, -180", "35.676200, 139.650300", "-22.906847, -43.172897" })
	void acceptsEveryPointOnEarth(String latitude, String longitude) {
		assertThat(at(latitude, longitude).latitude()).isEqualByComparingTo(latitude);
	}

	@ParameterizedTest(name = "({0}, {1})")
	@CsvSource({ "90.000001, 0", "-90.5, 0", "0, 180.000001", "0, -181" })
	void rejectsPointsOffTheMap(String latitude, String longitude) {
		assertThatThrownBy(() -> at(latitude, longitude))
				.isInstanceOf(InvalidPokemonDataException.class)
				.extracting(e -> ((InvalidPokemonDataException) e).field()).isEqualTo("coordinates");
	}

	@Test
	void atMostSixDecimalsWhichIsAbout11Centimetres() {
		assertThatThrownBy(() -> at("35.1234567", "139")).isInstanceOf(InvalidPokemonDataException.class);
	}

	@Test
	void bothHalvesAreRequired() {
		assertThatThrownBy(() -> new Coordinates(null, BigDecimal.ONE)).isInstanceOf(InvalidPokemonDataException.class);
		assertThatThrownBy(() -> new Coordinates(BigDecimal.ONE, null)).isInstanceOf(InvalidPokemonDataException.class);
	}
}
