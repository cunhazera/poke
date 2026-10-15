package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PokemonSummaryTest {

	private static final Weight WEIGHT = Weight.ofHectograms(69);

	@Test
	void abilitiesAreDefensivelyCopied() {
		var abilities = new ArrayList<>(List.of(new Ability("overgrow", false)));
		var summary = new PokemonSummary(1, "bulbasaur", null, "Seed Pokémon", WEIGHT, abilities);

		abilities.add(new Ability("chlorophyll", true));

		assertThat(summary.abilities()).containsExactly(new Ability("overgrow", false));
	}

	@Test
	void spriteAndCategoryAreOptional() {
		var summary = new PokemonSummary(1, "bulbasaur", null, null, WEIGHT, List.of());

		assertThat(summary.spriteUrl()).isNull();
		assertThat(summary.category()).isNull();
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1 })
	void rejectsNonPositiveId(int id) {
		assertThatThrownBy(() -> new PokemonSummary(id, "bulbasaur", null, null, WEIGHT, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = "  ")
	void rejectsBlankName(String name) {
		assertThatThrownBy(() -> new PokemonSummary(1, name, null, null, WEIGHT, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsMissingWeightOrAbilities() {
		assertThatThrownBy(() -> new PokemonSummary(1, "bulbasaur", null, null, null, List.of()))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new PokemonSummary(1, "bulbasaur", null, null, WEIGHT, null))
				.isInstanceOf(NullPointerException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void abilityRequiresAName(String name) {
		assertThatThrownBy(() -> new Ability(name, false)).isInstanceOf(IllegalArgumentException.class);
	}
}
