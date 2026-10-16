package com.app.poke.application.usecase;

import static com.app.poke.application.usecase.CatalogFixtures.STATS;

import java.util.List;

import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.Height;
import com.app.poke.domain.model.PokedexEntry;
import com.app.poke.domain.model.Weight;

final class LocalPokemonFixtures {

	private LocalPokemonFixtures() {
	}

	static PokedexEntry entry(String name, String description) {
		return new PokedexEntry(name, "Seed Pokémon", description, Height.ofDecimetres(7), Weight.ofHectograms(69),
				"https://sprites/1.png", "https://art/1.png", List.of("grass"), List.of(new Ability("overgrow", false)), STATS);
	}
}
