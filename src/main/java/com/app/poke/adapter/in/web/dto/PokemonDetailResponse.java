package com.app.poke.adapter.in.web.dto;

import java.util.List;

/**
 * The US02 detail view of one Pokémon.
 *
 * @param imageUrl the official artwork; null when PokeAPI has none
 * @param description the latest English Pokédex entry; null when none exists
 * @param heightM height in metres (PokeAPI reports decimetres)
 * @param weightKg weight in kilograms (PokeAPI reports hectograms)
 * @param evolutionChain the whole chain from its first stage, as a tree
 */
public record PokemonDetailResponse(
		int id,
		String name,
		String category,
		String imageUrl,
		String spriteUrl,
		String description,
		double heightM,
		double weightKg,
		List<String> types,
		List<AbilityResponse> abilities,
		BaseStatsResponse stats,
		EvolutionStageResponse evolutionChain) {

	public PokemonDetailResponse {
		types = List.copyOf(types);
		abilities = List.copyOf(abilities);
	}
}
