package com.app.poke.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A Pokémon in our own store (US03): the catalog fields plus our proprietary ones.
 *
 * @param pokedexNumber null for a Pokémon created locally
 * @param localizedNames names by BCP 47 language tag, sorted by language
 * @param coordinates null when not set
 * @param syncedAt when the catalog fields were last copied from PokeAPI; null for a Pokémon created locally
 * @param version send it back on updates (US04) to detect concurrent changes
 */
public record LocalPokemonResponse(
		long id,
		Integer pokedexNumber,
		String name,
		String category,
		String description,
		double heightM,
		double weightKg,
		String spriteUrl,
		String imageUrl,
		List<String> types,
		List<AbilityResponse> abilities,
		BaseStatsResponse stats,
		Map<String, String> localizedNames,
		String region,
		String habitat,
		CoordinatesResponse coordinates,
		List<String> tags,
		Instant syncedAt,
		long version) {
}
