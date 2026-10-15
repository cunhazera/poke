package com.app.poke.adapter.in.web.dto;

import java.util.List;

/**
 * One entry in the US01 paginated listing.
 *
 * @param category the species genus, e.g. "Seed Pokémon"
 * @param weightKg the weight in kilograms (PokeAPI reports hectograms)
 */
public record PokemonSummaryResponse(
		int id,
		String name,
		String spriteUrl,
		String category,
		double weightKg,
		List<AbilityResponse> abilities) {

	public PokemonSummaryResponse {
		abilities = List.copyOf(abilities);
	}
}
