package com.app.poke.application.usecase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntConsumer;

import com.app.poke.application.port.out.CatalogSlice;
import com.app.poke.application.port.out.PokemonCatalogGateway;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.application.port.out.SpeciesProfile;
import com.app.poke.domain.exception.PokemonNotFoundException;

/** In-memory catalog: behaves like a real gateway (ordering, paging, not-found) without network. */
class FakePokemonCatalog implements PokemonCatalogGateway {

	private final List<PokemonProfile> entries = new ArrayList<>();
	private final Map<Integer, String> categories = new HashMap<>();
	private final Map<Integer, RuntimeException> failures = new HashMap<>();
	private final List<Integer> unlisted = new ArrayList<>();
	private final List<Integer> speciesless = new ArrayList<>();
	final List<String> calls = Collections.synchronizedList(new ArrayList<>());
	private IntConsumer onFetch = id -> { };

	FakePokemonCatalog add(PokemonProfile profile, String category) {
		entries.add(profile);
		if (category != null) {
			categories.put(profile.id(), category);
		}
		return this;
	}

	FakePokemonCatalog failOn(int id, RuntimeException failure) {
		failures.put(id, failure);
		return this;
	}

	/** Simulates upstream inconsistency: listed, but its detail endpoint returns 404. */
	FakePokemonCatalog listedButMissing(int id) {
		unlisted.add(id);
		return this;
	}

	/** The species endpoint doesn't know this id (PokeAPI answers 404). */
	FakePokemonCatalog withoutSpecies(int id) {
		speciesless.add(id);
		return this;
	}

	FakePokemonCatalog onFetch(IntConsumer hook) {
		this.onFetch = hook;
		return this;
	}

	@Override
	public CatalogSlice listEntries(int offset, int limit) {
		calls.add("list(" + offset + "," + limit + ")");
		var ids = entries.stream().skip(offset).limit(limit).map(PokemonProfile::id).toList();
		return new CatalogSlice(ids, entries.size());
	}

	@Override
	public PokemonProfile findPokemon(int id) {
		calls.add("pokemon(" + id + ")");
		onFetch.accept(id);
		if (failures.containsKey(id)) {
			throw failures.get(id);
		}
		if (unlisted.contains(id)) {
			throw new PokemonNotFoundException(id);
		}
		return entries.stream().filter(p -> p.id() == id).findFirst()
				.orElseThrow(() -> new PokemonNotFoundException(id));
	}

	@Override
	public Optional<SpeciesProfile> findSpecies(int id) {
		calls.add("species(" + id + ")");
		onFetch.accept(id);
		if (speciesless.contains(id) || entries.stream().noneMatch(p -> p.id() == id)) {
			return Optional.empty();
		}
		return Optional.of(new SpeciesProfile(categories.get(id)));
	}
}
