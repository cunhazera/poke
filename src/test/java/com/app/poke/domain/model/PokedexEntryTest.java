package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PokedexEntryTest {

	private static final BaseStats STATS = new BaseStats(45, 49, 49, 65, 65, 45);

	private static PokedexEntry withTypes(List<String> types) {
		return new PokedexEntry("bulbasaur", null, null, Height.ofDecimetres(7), Weight.ofHectograms(69), null, null, types,
				List.of(), STATS);
	}

	@Test
	void textsAndImagesAreOptional() {
		var entry = withTypes(List.of("grass"));

		assertThat(entry.category()).isNull();
		assertThat(entry.description()).isNull();
		assertThat(entry.spriteUrl()).isNull();
	}

	@Test
	void hasOneOrTwoTypes() {
		assertThat(withTypes(List.of("grass", "poison")).types()).hasSize(2);
		assertThatThrownBy(() -> withTypes(List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> withTypes(List.of("a", "b", "c"))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void atMostThreeAbilitiesTheSchemaHasSlotsFor() {
		var four = List.of(new Ability("a", false), new Ability("b", false), new Ability("c", false), new Ability("d", true));

		assertThatThrownBy(() -> new PokedexEntry("x", null, null, Height.ofDecimetres(1), Weight.ofHectograms(1), null,
				null, List.of("normal"), four, STATS)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void collectionsAreDefensivelyCopied() {
		var types = new ArrayList<>(List.of("grass"));
		var entry = withTypes(types);

		types.add("poison");

		assertThat(entry.types()).containsExactly("grass");
	}

	@Test
	void requiresANameAndTheMeasurements() {
		assertThatThrownBy(() -> new PokedexEntry(" ", null, null, Height.ofDecimetres(1), Weight.ofHectograms(1), null,
				null, List.of("normal"), List.of(), STATS)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PokedexEntry("x", null, null, null, Weight.ofHectograms(1), null, null,
				List.of("normal"), List.of(), STATS)).isInstanceOf(NullPointerException.class);
	}
}
