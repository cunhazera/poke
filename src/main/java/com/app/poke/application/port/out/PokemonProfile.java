package com.app.poke.application.port.out;

import java.util.List;
import java.util.Objects;

import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.BaseStats;
import com.app.poke.domain.model.Height;
import com.app.poke.domain.model.Weight;

/**
 * The per-Pokémon data a catalog provides, before it is combined with species data.
 *
 * @param spriteUrl the small default sprite; may be null
 * @param imageUrl the official artwork; may be null
 * @param types in slot order
 * @param abilities in slot order
 */
public record PokemonProfile(int id, String name, String spriteUrl, String imageUrl, Height height, Weight weight,
		List<String> types, List<Ability> abilities, BaseStats stats) {

	public PokemonProfile {
		Objects.requireNonNull(height, "height");
		Objects.requireNonNull(weight, "weight");
		Objects.requireNonNull(stats, "stats");
		types = List.copyOf(types);
		abilities = List.copyOf(abilities);
	}
}
