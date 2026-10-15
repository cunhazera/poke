package com.app.poke.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The listing view of a Pokémon (US01).
 *
 * @param spriteUrl may be null: some Pokémon have no default sprite
 * @param category the species genus, e.g. "Seed Pokémon"; null when no English genus exists
 */
public record PokemonSummary(int id, String name, String spriteUrl, String category, Weight weight, List<Ability> abilities) {

	public PokemonSummary {
		if (id < 1) {
			throw new IllegalArgumentException("id must be positive, was " + id);
		}
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		Objects.requireNonNull(weight, "weight");
		abilities = List.copyOf(Objects.requireNonNull(abilities, "abilities"));
	}
}
