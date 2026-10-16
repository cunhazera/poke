package com.app.poke.adapter.out.pokeapi;

import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.out.CatalogSlice;
import com.app.poke.application.port.out.EvolutionLink;
import com.app.poke.application.port.out.PokemonCatalogGateway;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.application.port.out.SpeciesProfile;
import com.app.poke.domain.exception.PokemonNotFoundException;

/**
 * PokeAPI-backed catalog. Lists from {@code /pokemon-species} (one entry per species, no alternate forms), and
 * relies on a species' default Pokémon sharing its id. Responses are cached because PokeAPI data is static and its
 * fair-use policy asks clients to cache.
 */
public class PokeApiCatalogGateway implements PokemonCatalogGateway {

	public static final String ENTRIES_CACHE = "pokeapi-entries";
	public static final String POKEMON_CACHE = "pokeapi-pokemon";
	public static final String SPECIES_CACHE = "pokeapi-species";
	public static final String EVOLUTION_CACHE = "pokeapi-evolution";

	private final RestClient client;

	public PokeApiCatalogGateway(RestClient client) {
		this.client = client;
	}

	@Override
	@Cacheable(ENTRIES_CACHE)
	public CatalogSlice listEntries(int offset, int limit) {
		var list = call("species list", () -> client.get()
				.uri("/pokemon-species?offset={offset}&limit={limit}", offset, limit)
				.retrieve().body(PokeApiDtos.ResourceList.class));
		return mapped("species list", () -> PokeApiMapper.toSlice(list));
	}

	@Override
	@Cacheable(POKEMON_CACHE)
	public PokemonProfile findPokemon(int id) {
		try {
			var pokemon = call("pokemon " + id, () -> client.get()
					.uri("/pokemon/{id}", id).retrieve().body(PokeApiDtos.Pokemon.class));
			return mapped("pokemon " + id, () -> PokeApiMapper.toProfile(pokemon));
		} catch (HttpClientErrorException.NotFound e) {
			throw new PokemonNotFoundException(id);
		}
	}

	@Override
	@Cacheable(SPECIES_CACHE)
	public Optional<SpeciesProfile> findSpecies(int id) {
		try {
			var species = call("species " + id, () -> client.get()
					.uri("/pokemon-species/{id}", id).retrieve().body(PokeApiDtos.Species.class));
			return Optional.of(mapped("species " + id, () -> PokeApiMapper.toSpecies(species)));
		} catch (HttpClientErrorException.NotFound e) {
			return Optional.empty();
		}
	}

	@Override
	@Cacheable(EVOLUTION_CACHE)
	public EvolutionLink findEvolutionChain(int chainId) {
		try {
			var chain = call("evolution chain " + chainId, () -> client.get()
					.uri("/evolution-chain/{id}", chainId).retrieve().body(PokeApiDtos.EvolutionChain.class));
			if (chain.chain() == null) {
				throw new CatalogUnavailableException("PokeAPI evolution chain %d is empty".formatted(chainId), null);
			}
			return mapped("evolution chain " + chainId, () -> PokeApiMapper.toEvolutionLink(chain.chain()));
		} catch (HttpClientErrorException.NotFound e) {
			throw new CatalogUnavailableException("PokeAPI has no evolution chain " + chainId, e);
		}
	}

	/** Runs a request, letting 404s through for the caller to interpret and translating every other failure. */
	private static <T> T call(String what, Supplier<T> request) {
		T body;
		try {
			body = request.get();
		} catch (HttpClientErrorException.NotFound e) {
			throw e;
		} catch (RestClientException e) {
			throw new CatalogUnavailableException("PokeAPI request failed: " + what, e);
		}
		if (body == null) {
			throw new CatalogUnavailableException("PokeAPI returned an empty body: " + what, null);
		}
		return body;
	}

	/** Upstream data that breaks a domain invariant (a stat of 0, a missing name) is an upstream failure, not our bug. */
	private static <T> T mapped(String what, Supplier<T> mapping) {
		try {
			return mapping.get();
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new CatalogUnavailableException("PokeAPI returned invalid data: " + what, e);
		}
	}
}
