package com.app.poke.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * US04: a full replacement of a local Pokémon's editable data. For a synced Pokémon send only the proprietary fields;
 * for one created locally send the catalog fields too (as when creating it). Omitted proprietary fields are cleared.
 *
 * @param version the version from the last read; a stale one is a 409
 */
public record UpdateLocalPokemonRequest(
		@NotNull Long version,
		String name,
		String category,
		String description,
		BigDecimal heightM,
		BigDecimal weightKg,
		String spriteUrl,
		String imageUrl,
		List<@NotNull String> types,
		List<@NotNull @Valid AbilityRequest> abilities,
		@Valid StatsRequest stats,
		Map<String, String> localizedNames,
		String region,
		String habitat,
		@Valid CoordinatesRequest coordinates,
		List<@NotNull String> tags) {

	/** Whether any catalog field was sent, i.e. whether this edit replaces the catalog part too. */
	public boolean hasCatalogFields() {
		return Stream.of(name, category, description, heightM, weightKg, spriteUrl, imageUrl, types, abilities, stats)
				.anyMatch(field -> field != null);
	}
}
