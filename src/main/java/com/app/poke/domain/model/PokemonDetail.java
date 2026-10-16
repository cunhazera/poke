package com.app.poke.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The detail view of a Pokémon (US02).
 *
 * @param category the species genus; null when no English genus exists
 * @param imageUrl the official artwork; may be null
 * @param spriteUrl the small default sprite; may be null
 * @param description the Pokédex flavor text; null when none exists in English
 * @param types one or two, in slot order
 * @param evolutionChain the whole chain from its first stage; it always contains this Pokémon
 */
public record PokemonDetail(
		int id,
		String name,
		String category,
		String imageUrl,
		String spriteUrl,
		String description,
		Height height,
		Weight weight,
		List<String> types,
		List<Ability> abilities,
		BaseStats stats,
		EvolutionStage evolutionChain) {

	public PokemonDetail {
		if (id < 1) {
			throw new IllegalArgumentException("id must be positive, was " + id);
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		Objects.requireNonNull(height, "height");
		Objects.requireNonNull(weight, "weight");
		Objects.requireNonNull(stats, "stats");
		Objects.requireNonNull(evolutionChain, "evolutionChain");
		types = List.copyOf(Objects.requireNonNull(types, "types"));
		if (types.isEmpty() || types.size() > 2 || types.stream().anyMatch(String::isBlank)) {
			throw new IllegalArgumentException("a Pokémon has one or two non-blank types, was " + types);
		}
		abilities = List.copyOf(Objects.requireNonNull(abilities, "abilities"));
		if (!evolutionChain.contains(id)) {
			throw new IllegalArgumentException("evolution chain does not contain Pokémon " + id);
		}
	}
}
