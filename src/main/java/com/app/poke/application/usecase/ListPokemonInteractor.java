package com.app.poke.application.usecase;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
 * Lists one page of Pokémon. The catalog only returns ids for a page, so each entry's profile and
 * category are fetched concurrently on {@code executor} and reassembled in catalog order.
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
		var slice = catalog.listEntries(request.offset(), request.size());

		List<CompletableFuture<PokemonSummary>> summaries = slice.ids().stream().map(this::summarize).toList();

		return new Page<>(summaries.stream().map(ListPokemonInteractor::join).toList(), request, slice.totalCount());
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

	private static <T> T join(CompletableFuture<T> future) {
		try {
			return future.join();
		} catch (CompletionException e) {
			if (e.getCause() instanceof RuntimeException cause) {
				throw cause;
			}
			throw e;
		}
	}
}
