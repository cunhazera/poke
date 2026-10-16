package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PokemonTypeTest {

	@Test
	void thereAreTheEighteenTypesOfTheGames() {
		assertThat(PokemonType.values()).hasSize(18);
	}

	@ParameterizedTest
	@ValueSource(strings = { "fire", "water", "grass", "electric", "fairy", "dark", "steel" })
	void knowsTheCanonicalNames(String name) {
		assertThat(PokemonType.isCanonical(name)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "fyre", "Fire", "FIRE", " fire", "stellar", "unknown", "" })
	void rejectsTyposCapitalsAndNonStandardTypes(String name) {
		assertThat(PokemonType.isCanonical(name)).isFalse();
	}

	@Test
	void namesAreTheLowercaseApiNames() {
		assertThat(PokemonType.FIRE.apiName()).isEqualTo("fire");
	}

	@Test
	void nullIsNotAType() {
		assertThat(PokemonType.isCanonical(null)).isFalse();
	}
}
