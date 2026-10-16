package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.app.poke.domain.exception.InvalidPokemonIdException;

class PokedexNumberTest {

	@ParameterizedTest
	@ValueSource(ints = { 1, 25, 1025, Integer.MAX_VALUE })
	void acceptsPositiveNumbers(int value) {
		assertThat(new PokedexNumber(value).value()).isEqualTo(value);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, Integer.MIN_VALUE })
	void rejectsZeroAndNegativesWithADomainError(int value) {
		assertThatThrownBy(() -> new PokedexNumber(value))
				.isInstanceOf(InvalidPokemonIdException.class)
				.hasMessageContaining(String.valueOf(value));
	}

	@Test
	void numbersWithTheSameValueAreEqual() {
		assertThat(new PokedexNumber(7)).isEqualTo(new PokedexNumber(7));
	}
}
