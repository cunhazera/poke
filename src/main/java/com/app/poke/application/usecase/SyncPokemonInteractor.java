package com.app.poke.application.usecase;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.in.SyncPokemonCommand;
import com.app.poke.application.port.in.SyncPokemonUseCase;
import com.app.poke.application.port.in.SyncResult;
import com.app.poke.application.port.out.LocalPokemonGateway;
import com.app.poke.application.port.out.PokemonCatalogGateway;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.application.port.out.SpeciesProfile;
import com.app.poke.domain.exception.PokemonNotFoundException;
import com.app.poke.domain.model.LocalPokemon;
import com.app.poke.domain.model.PokedexEntry;

/**
 * Copies a catalog Pokémon into our store. Everything is fetched from the catalog first, so an upstream failure changes
 * nothing; then the copy is created, or refreshed keeping its proprietary data (the rule lives in {@link LocalPokemon}).
 */
public final class SyncPokemonInteractor implements SyncPokemonUseCase {

	private final PokemonCatalogGateway catalog;
	private final LocalPokemonGateway store;
	private final Executor executor;
	private final Clock clock;

	public SyncPokemonInteractor(PokemonCatalogGateway catalog, LocalPokemonGateway store, Executor executor, Clock clock) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
		this.store = Objects.requireNonNull(store, "store");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public SyncResult sync(SyncPokemonCommand command) {
		var number = command.pokedexNumber();
		var id = number.value();
		var profileFuture = CompletableFuture.supplyAsync(() -> catalog.findPokemon(id), executor);
		var speciesFuture = CompletableFuture.supplyAsync(
				() -> catalog.findSpecies(id).orElseThrow(() -> new PokemonNotFoundException(id)), executor);
		var entry = toEntry(id, Futures.join(profileFuture), Futures.join(speciesFuture));

		var now = clock.instant();
		var existing = store.findByPokedexNumber(id);
		var copy = existing.map(stored -> stored.refreshedFrom(entry, now))
				.orElseGet(() -> LocalPokemon.fromCatalog(number, entry, now));
		return new SyncResult(store.save(copy), existing.isEmpty());
	}

	private static PokedexEntry toEntry(int id, PokemonProfile profile, SpeciesProfile species) {
		try {
			return new PokedexEntry(profile.name(), species.category(), species.description(), profile.height(),
					profile.weight(), profile.spriteUrl(), profile.imageUrl(), profile.types(), profile.abilities(),
					profile.stats());
		} catch (IllegalArgumentException e) {
			throw new CatalogUnavailableException("Catalog returned invalid data for pokemon " + id, e);
		}
	}
}
