package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PokemonDetailTest {

	private static final BaseStats STATS = new BaseStats(45, 49, 49, 65, 65, 45);
	private static final EvolutionStage CHAIN = new EvolutionStage(1, "bulbasaur", null,
			List.of(new EvolutionStage(2, "ivysaur", null, List.of())));

	private static PokemonDetail detail(int id, List<String> types, EvolutionStage chain) {
		return new PokemonDetail(id, "ivysaur", "Seed Pokémon", "https://art/2.png", "https://sprites/2.png", "A bulb…",
				Height.ofDecimetres(10), Weight.ofHectograms(130), types, List.of(new Ability("overgrow", false)), STATS, chain);
	}

	@Test
	void thePokemonMayBeAnyStageOfItsChain() {
		assertThatCode(() -> detail(2, List.of("grass"), CHAIN)).as("a middle stage").doesNotThrowAnyException();
		assertThatCode(() -> detail(1, List.of("grass"), CHAIN)).as("the root").doesNotThrowAnyException();
	}

	@Test
	void rejectsAChainThatDoesNotContainThePokemon() {
		assertThatThrownBy(() -> detail(133, List.of("normal"), CHAIN))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("133");
	}

	@Test
	void optionalTextsMayBeMissing() {
		var bare = new PokemonDetail(2, "ivysaur", null, null, null, null, Height.ofDecimetres(10),
				Weight.ofHectograms(130), List.of("grass"), List.of(), STATS, CHAIN);

		assertThat(bare.category()).isNull();
		assertThat(bare.description()).isNull();
		assertThat(bare.imageUrl()).isNull();
	}

	@Test
	void hasOneOrTwoNonBlankTypes() {
		assertThatCode(() -> detail(2, List.of("grass", "poison"), CHAIN)).doesNotThrowAnyException();
		assertThatThrownBy(() -> detail(2, List.of(), CHAIN)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> detail(2, List.of("a", "b", "c"), CHAIN)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> detail(2, List.of(" "), CHAIN)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void collectionsAreDefensivelyCopied() {
		var types = new ArrayList<>(List.of("grass"));
		var detail = detail(2, types, CHAIN);

		types.add("poison");

		assertThat(detail.types()).containsExactly("grass");
	}

	@Test
	void requiresTheCoreFields() {
		assertThatThrownBy(() -> new PokemonDetail(2, " ", null, null, null, null, Height.ofDecimetres(1),
				Weight.ofHectograms(1), List.of("grass"), List.of(), STATS, CHAIN)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PokemonDetail(2, "ivysaur", null, null, null, null, Height.ofDecimetres(1),
				Weight.ofHectograms(1), List.of("grass"), List.of(), null, CHAIN)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new PokemonDetail(2, "ivysaur", null, null, null, null, Height.ofDecimetres(1),
				Weight.ofHectograms(1), List.of("grass"), List.of(), STATS, null)).isInstanceOf(NullPointerException.class);
	}
}
