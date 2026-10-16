package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.app.poke.domain.exception.InvalidPokemonDataException;

class WeightTest {

	@ParameterizedTest(name = "{0} hg = {1} kg")
	@CsvSource({ "69, 6.9", "0, 0.0", "1, 0.1", "9999, 999.9", "60, 6.0" })
	void convertsHectogramsToKilograms(int hectograms, double expectedKg) {
		assertThat(Weight.ofHectograms(hectograms).kilograms()).isEqualTo(expectedKg);
	}

	@Test
	void rejectsNegativeWeight() {
		assertThatThrownBy(() -> Weight.ofHectograms(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({ "0.4, 4", "1, 10", "0.0, 0", "2.50, 25", "10000.0, 100000" })
	void isAuthoredWithAtMostOneDecimal(String value, int expected) {
		assertThat(Weight.ofKilograms(new BigDecimal(value)).hectograms()).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.25", "-0.1", "10000.1" })
	void rejectsMoreThanOneDecimalNegativeOrTooLarge(String value) {
		assertThatThrownBy(() -> Weight.ofKilograms(new BigDecimal(value)))
				.isInstanceOfSatisfying(InvalidPokemonDataException.class, e -> assertThat(e.field()).isEqualTo("weightKg"));
	}

	@Test
	void isRequiredWhenAuthored() {
		assertThatThrownBy(() -> Weight.ofKilograms(null))
				.isInstanceOfSatisfying(InvalidPokemonDataException.class, e -> assertThat(e.field()).isEqualTo("weightKg"));
	}
}
