package com.app.poke.adapter.out.pokeapi;

import java.util.Comparator;
import java.util.List;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.out.CatalogSlice;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.application.port.out.SpeciesProfile;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.Weight;

/** Translates PokeAPI payloads into the gateway's types. */
final class PokeApiMapper {

	private static final String ENGLISH = "en";

	private PokeApiMapper() {
	}

	static CatalogSlice toSlice(PokeApiDtos.ResourceList list) {
		var ids = nullToEmpty(list.results()).stream().map(r -> idFromUrl(r.url())).toList();
		return new CatalogSlice(ids, list.count());
	}

	static PokemonProfile toProfile(PokeApiDtos.Pokemon pokemon) {
		var sprite = pokemon.sprites() == null ? null : pokemon.sprites().frontDefault();
		var abilities = nullToEmpty(pokemon.abilities()).stream()
				.sorted(Comparator.comparingInt(PokeApiDtos.AbilitySlot::slot))
				.map(slot -> new Ability(slot.ability().name(), slot.hidden()))
				.toList();
		return new PokemonProfile(pokemon.id(), pokemon.name(), sprite, Weight.ofHectograms(pokemon.weight()), abilities);
	}

	static SpeciesProfile toSpecies(PokeApiDtos.Species species) {
		var genus = nullToEmpty(species.genera()).stream()
				.filter(g -> g.language() != null && ENGLISH.equals(g.language().name()))
				.map(PokeApiDtos.Genus::genus)
				.findFirst()
				.orElse(null);
		return new SpeciesProfile(genus);
	}

	/** PokeAPI resource URLs end with the id: {@code .../pokemon-species/25/}. */
	static int idFromUrl(String url) {
		var trimmed = url == null ? "" : url.replaceAll("/+$", "");
		var id = trimmed.substring(trimmed.lastIndexOf('/') + 1);
		try {
			return Integer.parseInt(id);
		} catch (NumberFormatException e) {
			throw new CatalogUnavailableException("Unexpected PokeAPI resource URL: " + url, e);
		}
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}
}
