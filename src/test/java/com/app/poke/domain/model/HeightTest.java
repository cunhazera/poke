package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.app.poke.domain.exception.InvalidPokemonDataException;

class HeightTest {

	@ParameterizedTest(name = "{0} dm = {1} m")
	@CsvSource({ "7, 0.7", "3, 0.3", "0, 0.0", "20, 2.0", "1000, 100.0" })
	void convertsDecimetresToMetres(int decimetres, double expectedMetres) {
		assertThat(Height.ofDecimetres(decimetres).metres()).isEqualTo(expectedMetres);
	}

	@Test
	void rejectsNegativeHeight() {
		assertThatThrownBy(() -> Height.ofDecimetres(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({ "0.4, 4", "1, 10", "0.0, 0", "2.50, 25", "100.0, 1000" })
	void isAuthoredWithAtMostOneDecimal(String value, int expected) {
		assertThat(Height.ofMetres(new BigDecimal(value)).decimetres()).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0.25", "-0.1", "100.1" })
	void rejectsMoreThanOneDecimalNegativeOrTooLarge(String value) {
		assertThatThrownBy(() -> Height.ofMetres(new BigDecimal(value)))
				.isInstanceOfSatisfying(InvalidPokemonDataException.class, e -> assertThat(e.field()).isEqualTo("heightM"));
	}

	@Test
	void isRequiredWhenAuthored() {
		assertThatThrownBy(() -> Height.ofMetres(null))
				.isInstanceOfSatisfying(InvalidPokemonDataException.class, e -> assertThat(e.field()).isEqualTo("heightM"));
	}
}
