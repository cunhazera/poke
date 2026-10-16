package com.app.poke.application.port.out;

import java.util.List;

/**
 * One node of an evolution chain as the catalog describes it: who evolves into whom, without per-stage details.
 *
 * @param speciesId the species' Pokédex number
 */
public record EvolutionLink(int speciesId, String name, List<EvolutionLink> evolvesTo) {

	public EvolutionLink {
		evolvesTo = List.copyOf(evolvesTo);
	}

	public static EvolutionLink single(int speciesId, String name) {
		return new EvolutionLink(speciesId, name, List.of());
	}
}
