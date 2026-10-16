package com.app.poke.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * One node of an evolution chain. Chains are trees, not lists: Eevee evolves into eight species.
 *
 * @param id the species' Pokédex number
 * @param spriteUrl may be null when the species has no default sprite
 * @param evolvesTo the direct evolutions, in catalog order; empty for the last stage
 */
public record EvolutionStage(int id, String name, String spriteUrl, List<EvolutionStage> evolvesTo) {

	public EvolutionStage {
		if (id < 1) {
			throw new IllegalArgumentException("id must be positive, was " + id);
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		evolvesTo = List.copyOf(Objects.requireNonNull(evolvesTo, "evolvesTo"));
	}

	/** Whether this stage or any later one is the given species. */
	public boolean contains(int speciesId) {
		return id == speciesId || evolvesTo.stream().anyMatch(next -> next.contains(speciesId));
	}
}
