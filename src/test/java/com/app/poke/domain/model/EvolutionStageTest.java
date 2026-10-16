package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class EvolutionStageTest {

	private static EvolutionStage stage(int id, String name, EvolutionStage... evolvesTo) {
		return new EvolutionStage(id, name, "https://sprites/" + id + ".png", List.of(evolvesTo));
	}

	private final EvolutionStage eevee = stage(133, "eevee", stage(134, "vaporeon"), stage(135, "jolteon"));
	private final EvolutionStage bulbasaur = stage(1, "bulbasaur", stage(2, "ivysaur", stage(3, "venusaur")));

	@Test
	void findsTheRootABranchAndADeepDescendant() {
		assertThat(eevee.contains(133)).isTrue();
		assertThat(eevee.contains(135)).as("second branch").isTrue();
		assertThat(bulbasaur.contains(3)).as("grandchild").isTrue();
	}

	@Test
	void doesNotFindPokemonOutsideTheChain() {
		assertThat(eevee.contains(1)).isFalse();
		assertThat(bulbasaur.contains(133)).isFalse();
	}

	@Test
	void aSingleStageChainHasNoEvolutions() {
		var tauros = stage(128, "tauros");

		assertThat(tauros.evolvesTo()).isEmpty();
		assertThat(tauros.contains(128)).isTrue();
	}

	@Test
	void branchesKeepTheirOrder() {
		assertThat(eevee.evolvesTo()).extracting(EvolutionStage::name).containsExactly("vaporeon", "jolteon");
	}

	@Test
	void isImmutableEvenIfTheSourceListChanges() {
		var children = new ArrayList<>(List.of(stage(2, "ivysaur")));
		var root = new EvolutionStage(1, "bulbasaur", null, children);

		children.add(stage(999, "intruder"));

		assertThat(root.evolvesTo()).hasSize(1);
		assertThatThrownBy(() -> root.evolvesTo().add(stage(4, "x"))).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void spriteIsOptional() {
		assertThat(new EvolutionStage(1, "bulbasaur", null, List.of()).spriteUrl()).isNull();
	}

	@Test
	void rejectsInvalidStages() {
		assertThatThrownBy(() -> new EvolutionStage(0, "x", null, List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new EvolutionStage(1, " ", null, List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new EvolutionStage(1, "x", null, null)).isInstanceOf(NullPointerException.class);
	}
}
