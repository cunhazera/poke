package com.app.poke.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The part of a local Pokémon that comes from the catalog (PokeAPI) and is refreshed by every sync.
 *
 * @param category the species genus; may be null
 * @param description the Pokédex flavor text; may be null
 * @param types one or two, in slot order
 * @param abilities at most three, in slot order
 */
public record PokedexEntry(String name, String category, String description, Height height, Weight weight,
		String spriteUrl, String imageUrl, List<String> types, List<Ability> abilities, BaseStats stats) {

	public static final int MAX_ABILITIES = 3;

	public PokedexEntry {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		Objects.requireNonNull(height, "height");
		Objects.requireNonNull(weight, "weight");
		Objects.requireNonNull(stats, "stats");
		types = List.copyOf(Objects.requireNonNull(types, "types"));
		if (types.isEmpty() || types.size() > 2 || types.stream().anyMatch(String::isBlank)) {
			throw new IllegalArgumentException("a Pokémon has one or two non-blank types, was " + types);
		}
		abilities = List.copyOf(Objects.requireNonNull(abilities, "abilities"));
		if (abilities.size() > MAX_ABILITIES) {
			throw new IllegalArgumentException("at most %d abilities, was %d".formatted(MAX_ABILITIES, abilities.size()));
		}
	}
}
