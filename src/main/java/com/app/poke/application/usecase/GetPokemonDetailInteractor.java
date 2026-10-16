package com.app.poke.application.usecase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.in.GetPokemonDetailQuery;
import com.app.poke.application.port.in.GetPokemonDetailUseCase;
import com.app.poke.application.port.out.EvolutionLink;
import com.app.poke.application.port.out.PokemonCatalogGateway;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.application.port.out.SpeciesProfile;
import com.app.poke.domain.exception.PokemonNotFoundException;
import com.app.poke.domain.model.EvolutionStage;
import com.app.poke.domain.model.PokemonDetail;

/**
 * Builds the detail view of one Pokémon. Profile and species are fetched concurrently; the species points at the
 * evolution chain, and the other stages of that chain are then fetched concurrently for their sprites.
 */
public final class GetPokemonDetailInteractor implements GetPokemonDetailUseCase {

	private final PokemonCatalogGateway catalog;
	private final Executor executor;

	public GetPokemonDetailInteractor(PokemonCatalogGateway catalog, Executor executor) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
		this.executor = Objects.requireNonNull(executor, "executor");
	}

	@Override
	public PokemonDetail get(GetPokemonDetailQuery query) {
		var id = query.pokedexNumber().value();
		var profileFuture = CompletableFuture.supplyAsync(() -> catalog.findPokemon(id), executor);
		var speciesFuture = CompletableFuture.supplyAsync(
				() -> catalog.findSpecies(id).orElseThrow(() -> new PokemonNotFoundException(id)), executor);
		var profile = Futures.join(profileFuture);
		var species = Futures.join(speciesFuture);

		var chain = evolutionChainOf(profile, species);
		var sprites = spritesOf(chain, profile);

		try {
			return new PokemonDetail(id, profile.name(), species.category(), profile.imageUrl(), profile.spriteUrl(),
					species.description(), profile.height(), profile.weight(), profile.types(), profile.abilities(),
					profile.stats(), toStage(chain, sprites));
		} catch (IllegalArgumentException e) {
			// Everything here came from the catalog: a broken rule means bad upstream data, not a bug of ours.
			throw new CatalogUnavailableException("Catalog returned invalid data for pokemon " + id, e);
		}
	}

	private EvolutionLink evolutionChainOf(PokemonProfile profile, SpeciesProfile species) {
		if (species.evolutionChainId() == null) {
			return EvolutionLink.single(profile.id(), profile.name());
		}
		var chain = catalog.findEvolutionChain(species.evolutionChainId());
		if (!speciesIds(chain).contains(profile.id())) {
			throw new CatalogUnavailableException("Evolution chain %d does not contain pokemon %d"
					.formatted(species.evolutionChainId(), profile.id()), null);
		}
		return chain;
	}

	/** The requested Pokémon's sprite is already known; every other stage is fetched concurrently. */
	private Map<Integer, String> spritesOf(EvolutionLink chain, PokemonProfile requested) {
		var others = speciesIds(chain).stream().filter(stageId -> stageId != requested.id())
				.collect(Collectors.toMap(Function.identity(),
						stageId -> CompletableFuture.supplyAsync(() -> stageSprite(stageId), executor)));
		var sprites = new HashMap<Integer, String>();
		sprites.put(requested.id(), requested.spriteUrl());
		others.forEach((stageId, sprite) -> sprites.put(stageId, Futures.join(sprite)));
		return sprites;
	}

	private String stageSprite(int stageId) {
		try {
			return catalog.findPokemon(stageId).spriteUrl();
		} catch (PokemonNotFoundException e) {
			throw new CatalogUnavailableException("Evolution chain lists pokemon %d but it could not be loaded"
					.formatted(stageId), e);
		}
	}

	private static EvolutionStage toStage(EvolutionLink link, Map<Integer, String> sprites) {
		var next = link.evolvesTo().stream().map(child -> toStage(child, sprites)).toList();
		return new EvolutionStage(link.speciesId(), link.name(), sprites.get(link.speciesId()), next);
	}

	private static List<Integer> speciesIds(EvolutionLink link) {
		var ids = new ArrayList<Integer>();
		ids.add(link.speciesId());
		link.evolvesTo().forEach(child -> ids.addAll(speciesIds(child)));
		return ids;
	}
}
