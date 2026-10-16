package com.app.poke.adapter.out.pokeapi;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.out.CatalogSlice;
import com.app.poke.application.port.out.EvolutionLink;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.application.port.out.SpeciesProfile;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.BaseStats;
import com.app.poke.domain.model.Height;
import com.app.poke.domain.model.Weight;

/** Translates PokeAPI payloads into the gateway's types. */
final class PokeApiMapper {

	private static final String ENGLISH = "en";
	/** A soft hyphen marks a word split across lines in old game text: "ener­\ngy" is "energy". */
	private static final Pattern SOFT_HYPHEN_BREAK = Pattern.compile("­\\s*");
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	private PokeApiMapper() {
	}

	static CatalogSlice toSlice(PokeApiDtos.ResourceList list) {
		var ids = nullToEmpty(list.results()).stream().map(r -> idFromUrl(r.url())).toList();
		return new CatalogSlice(ids, list.count());
	}

	static PokemonProfile toProfile(PokeApiDtos.Pokemon pokemon) {
		var sprites = pokemon.sprites();
		var sprite = sprites == null ? null : sprites.frontDefault();
		var artwork = sprites == null || sprites.other() == null || sprites.other().officialArtwork() == null
				? null : sprites.other().officialArtwork().frontDefault();
		var types = nullToEmpty(pokemon.types()).stream()
				.sorted(Comparator.comparingInt(PokeApiDtos.TypeSlot::slot))
				.map(slot -> slot.type().name())
				.toList();
		var abilities = nullToEmpty(pokemon.abilities()).stream()
				.sorted(Comparator.comparingInt(PokeApiDtos.AbilitySlot::slot))
				.map(slot -> new Ability(slot.ability().name(), slot.hidden()))
				.toList();
		return new PokemonProfile(pokemon.id(), pokemon.name(), sprite, artwork, Height.ofDecimetres(pokemon.height()),
				Weight.ofHectograms(pokemon.weight()), types, abilities, toStats(pokemon));
	}

	/** Stats are matched by name: PokeAPI's array order is not part of its contract. */
	private static BaseStats toStats(PokeApiDtos.Pokemon pokemon) {
		Map<String, Integer> byName = new HashMap<>();
		nullToEmpty(pokemon.stats()).forEach(s -> byName.put(s.stat().name(), s.baseStat()));
		return new BaseStats(stat(byName, "hp", pokemon), stat(byName, "attack", pokemon),
				stat(byName, "defense", pokemon), stat(byName, "special-attack", pokemon),
				stat(byName, "special-defense", pokemon), stat(byName, "speed", pokemon));
	}

	private static int stat(Map<String, Integer> byName, String name, PokeApiDtos.Pokemon pokemon) {
		var value = byName.get(name);
		if (value == null) {
			throw new CatalogUnavailableException("PokeAPI pokemon %d has no %s stat".formatted(pokemon.id(), name), null);
		}
		return value;
	}

	static SpeciesProfile toSpecies(PokeApiDtos.Species species) {
		var genus = nullToEmpty(species.genera()).stream()
				.filter(g -> isEnglish(g.language()))
				.map(PokeApiDtos.Genus::genus)
				.findFirst()
				.orElse(null);
		var description = nullToEmpty(species.flavorTextEntries()).stream()
				.filter(f -> isEnglish(f.language()))
				.map(PokeApiDtos.FlavorText::text)
				.filter(Predicate.not(text -> text == null || text.isBlank()))
				.reduce((older, newer) -> newer) // the most recent game's text comes last
				.map(PokeApiMapper::normalize)
				.orElse(null);
		var chainId = species.evolutionChain() == null ? null : idFromUrl(species.evolutionChain().url());
		return new SpeciesProfile(genus, description, chainId);
	}

	static EvolutionLink toEvolutionLink(PokeApiDtos.ChainLink link) {
		var next = nullToEmpty(link.evolvesTo()).stream().map(PokeApiMapper::toEvolutionLink).toList();
		return new EvolutionLink(idFromUrl(link.species().url()), link.species().name(), next);
	}

	/** Game text wraps with \n and pages with \f; both read as a single space. */
	static String normalize(String flavorText) {
		var joined = SOFT_HYPHEN_BREAK.matcher(flavorText).replaceAll("");
		return WHITESPACE.matcher(joined).replaceAll(" ").strip();
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

	private static boolean isEnglish(PokeApiDtos.NamedResource language) {
		return language != null && ENGLISH.equals(language.name());
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}
}
