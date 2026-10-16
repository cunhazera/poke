package com.app.poke.application.usecase;

import java.util.List;

import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.BaseStats;
import com.app.poke.domain.model.Height;
import com.app.poke.domain.model.Weight;

/** Valid catalog data for interactor tests; only the parts a test cares about vary by id. */
final class CatalogFixtures {

	static final BaseStats STATS = new BaseStats(45, 49, 49, 65, 65, 45);

	private CatalogFixtures() {
	}

	static PokemonProfile profile(int id, String name) {
		return new PokemonProfile(id, name, "https://sprites/" + id + ".png", "https://art/" + id + ".png",
				Height.ofDecimetres(id), Weight.ofHectograms(id * 10), List.of("normal"),
				List.of(new Ability(name + "-ability", false)), STATS);
	}
}
