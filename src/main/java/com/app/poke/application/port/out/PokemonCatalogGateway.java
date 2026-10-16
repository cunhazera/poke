package com.app.poke.application.port.out;

import java.util.List;
import java.util.Optional;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.domain.exception.PokemonNotFoundException;

/**
 * The external Pokémon catalog (PokeAPI). Entries are identified by their national Pokédex number.
 * Implementations throw {@link CatalogUnavailableException} for any upstream failure.
 */
public interface PokemonCatalogGateway {

	/** Every species, in Pokédex order: the index that listing and searching page through. */
	List<CatalogEntry> listAllEntries();

	/** @throws PokemonNotFoundException if the catalog has no such Pokémon */
	PokemonProfile findPokemon(int id);

	/** The species with this Pokédex number; empty if the catalog has no such species. */
	Optional<SpeciesProfile> findSpecies(int id);

	/**
	 * The evolution chain with this catalog id, from its first stage.
	 *
	 * @throws CatalogUnavailableException also if the chain is unknown: a species pointed at it, so the catalog is inconsistent
	 */
	EvolutionLink findEvolutionChain(int chainId);
}
