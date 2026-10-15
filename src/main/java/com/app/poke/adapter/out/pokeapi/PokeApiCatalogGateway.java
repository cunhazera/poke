package com.app.poke.adapter.out.pokeapi;

import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.out.CatalogSlice;
import com.app.poke.application.port.out.PokemonCatalogGateway;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.domain.exception.PokemonNotFoundException;

/**
 * PokeAPI-backed catalog. Lists from {@code /pokemon-species} (one entry per species, no alternate forms), and
 * relies on a species' default Pokémon sharing its id. Responses are cached because PokeAPI data is static and its
 * fair-use policy asks clients to cache.
 */
public class PokeApiCatalogGateway implements PokemonCatalogGateway {

	public static final String ENTRIES_CACHE = "pokeapi-entries";
	public static final String POKEMON_CACHE = "pokeapi-pokemon";
	public static final String CATEGORY_CACHE = "pokeapi-category";

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
		return PokeApiMapper.toSlice(list);
	}

	@Override
	@Cacheable(POKEMON_CACHE)
	public PokemonProfile findPokemon(int id) {
		try {
			return PokeApiMapper.toProfile(call("pokemon " + id, () -> client.get()
					.uri("/pokemon/{id}", id).retrieve().body(PokeApiDtos.Pokemon.class)));
		} catch (HttpClientErrorException.NotFound e) {
			throw new PokemonNotFoundException(id);
		}
	}

	@Override
	@Cacheable(CATEGORY_CACHE)
	public Optional<String> findCategory(int id) {
		try {
			return PokeApiMapper.englishGenus(call("species " + id, () -> client.get()
					.uri("/pokemon-species/{id}", id).retrieve().body(PokeApiDtos.Species.class)));
		} catch (HttpClientErrorException.NotFound e) {
			return Optional.empty();
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
}
