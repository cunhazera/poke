package com.app.poke.application.port.out;

import java.util.Optional;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.domain.exception.PokemonNotFoundException;

/**
 * The external Pokémon catalog (PokeAPI). Entries are identified by their national Pokédex number.
 * Implementations throw {@link CatalogUnavailableException} for any upstream failure.
 */
public interface PokemonCatalogGateway {

	/** The Pokédex numbers at the given position, in Pokédex order, plus the catalog's total size. */
	CatalogSlice listEntries(int offset, int limit);

	/** @throws PokemonNotFoundException if the catalog has no such Pokémon */
	PokemonProfile findPokemon(int id);

	/** The species category (genus), e.g. "Seed Pokémon"; empty if none is available in English. */
	Optional<String> findCategory(int id);
}
