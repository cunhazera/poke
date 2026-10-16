package com.app.poke.application.usecase;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.in.ListPokemonQuery;
import com.app.poke.application.port.in.ListPokemonUseCase;
import com.app.poke.application.port.out.PokemonCatalogGateway;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.application.port.out.SpeciesProfile;
import com.app.poke.domain.exception.PokemonNotFoundException;
import com.app.poke.domain.model.Page;
import com.app.poke.domain.model.PokemonSummary;

/**
 * Lists one page of Pokémon, optionally only those whose name contains a search. The catalog's index (every species'
 * number and name) is searched and paged here; then only that page's profiles and categories are fetched,
 * concurrently on {@code executor}, and reassembled in catalog order.
 */
public final class ListPokemonInteractor implements ListPokemonUseCase {

	private final PokemonCatalogGateway catalog;
	private final Executor executor;

	public ListPokemonInteractor(PokemonCatalogGateway catalog, Executor executor) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
		this.executor = Objects.requireNonNull(executor, "executor");
	}

	@Override
	public Page<PokemonSummary> list(ListPokemonQuery query) {
		var request = query.pageRequest();
		var index = catalog.listAllEntries();
		var matches = query.name()
				.map(name -> index.stream().filter(entry -> name.matchesSlug(entry.name())).toList())
				.orElse(index);

		List<CompletableFuture<PokemonSummary>> summaries = matches.stream()
				.skip(request.offset())
				.limit(request.size())
				.map(entry -> summarize(entry.id()))
				.toList();

		return new Page<>(summaries.stream().map(Futures::join).toList(), request, matches.size());
	}

	private CompletableFuture<PokemonSummary> summarize(int id) {
		var profile = CompletableFuture.supplyAsync(() -> findListedPokemon(id), executor);
		var category = CompletableFuture.supplyAsync(() -> catalog.findSpecies(id).map(SpeciesProfile::category), executor);
		return profile.thenCombine(category, ListPokemonInteractor::toSummary);
	}

	private PokemonProfile findListedPokemon(int id) {
		try {
			return catalog.findPokemon(id);
		} catch (PokemonNotFoundException e) {
			throw new CatalogUnavailableException("Catalog listed pokemon %d but could not load it".formatted(id), e);
		}
	}

	private static PokemonSummary toSummary(PokemonProfile profile, Optional<String> category) {
		return new PokemonSummary(profile.id(), profile.name(), profile.spriteUrl(), category.orElse(null),
				profile.weight(), profile.abilities());
	}
}
