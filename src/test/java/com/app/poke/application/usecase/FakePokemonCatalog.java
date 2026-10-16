package com.app.poke.application.usecase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntConsumer;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.out.CatalogSlice;
import com.app.poke.application.port.out.EvolutionLink;
import com.app.poke.application.port.out.PokemonCatalogGateway;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.application.port.out.SpeciesProfile;
import com.app.poke.domain.exception.PokemonNotFoundException;

/** In-memory catalog: behaves like a real gateway (ordering, paging, not-found) without network. */
class FakePokemonCatalog implements PokemonCatalogGateway {

	private final List<PokemonProfile> entries = new ArrayList<>();
	private final Map<Integer, SpeciesProfile> species = new HashMap<>();
	private final Map<Integer, EvolutionLink> chains = new HashMap<>();
	private final Map<Integer, RuntimeException> failures = new HashMap<>();
	private final List<Integer> unlisted = new ArrayList<>();
	final List<String> calls = Collections.synchronizedList(new ArrayList<>());
	private IntConsumer onFetch = id -> { };

	FakePokemonCatalog add(PokemonProfile profile, String category) {
		return add(profile, new SpeciesProfile(category, null, null));
	}

	FakePokemonCatalog add(PokemonProfile profile, SpeciesProfile speciesProfile) {
		entries.add(profile);
		species.put(profile.id(), speciesProfile);
		return this;
	}

	FakePokemonCatalog chain(int chainId, EvolutionLink root) {
		chains.put(chainId, root);
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

	/** The species endpoint doesn't know this id (PokeAPI answers 404), e.g. alternate forms like 10001. */
	FakePokemonCatalog withoutSpecies(int id) {
		species.remove(id);
		return this;
	}

	/** Runs on every pokemon and species fetch, with the id being fetched. */
	FakePokemonCatalog onFetch(IntConsumer hook) {
		this.onFetch = hook;
		return this;
	}

	long callsTo(String call) {
		synchronized (calls) {
			return calls.stream().filter(call::equals).count();
		}
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
		return Optional.ofNullable(species.get(id));
	}

	@Override
	public EvolutionLink findEvolutionChain(int chainId) {
		calls.add("chain(" + chainId + ")");
		var root = chains.get(chainId);
		if (root == null) {
			throw new CatalogUnavailableException("no evolution chain " + chainId, null);
		}
		return root;
	}
}
