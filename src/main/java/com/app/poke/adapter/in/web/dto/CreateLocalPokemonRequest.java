package com.app.poke.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * US04: a Pokémon that exists only in our store. Shape only (required fields present); the content rules live in the
 * domain. The proprietary fields are optional; unknown fields (such as {@code pokedexNumber}) are rejected.
 *
 * @param heightM metres, at most one decimal
 * @param weightKg kilograms, at most one decimal
 */
public record CreateLocalPokemonRequest(
		@NotNull String name,
		String category,
		String description,
		@NotNull BigDecimal heightM,
		@NotNull BigDecimal weightKg,
		String spriteUrl,
		String imageUrl,
		@NotNull List<@NotNull String> types,
		@NotNull List<@NotNull @Valid AbilityRequest> abilities,
		@NotNull @Valid StatsRequest stats,
		Map<String, String> localizedNames,
		String region,
		String habitat,
		@Valid CoordinatesRequest coordinates,
		List<@NotNull String> tags) {
}
