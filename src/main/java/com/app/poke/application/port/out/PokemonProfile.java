package com.app.poke.application.port.out;

import java.util.List;

import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.Weight;

/** The per-Pokémon data a catalog provides, before it is combined with species data. */
public record PokemonProfile(int id, String name, String spriteUrl, Weight weight, List<Ability> abilities) {

	public PokemonProfile {
		abilities = List.copyOf(abilities);
	}
}
