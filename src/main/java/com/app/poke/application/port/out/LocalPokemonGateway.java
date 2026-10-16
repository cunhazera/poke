package com.app.poke.application.port.out;

import java.util.Optional;

import com.app.poke.application.exception.ConcurrentUpdateException;
import com.app.poke.domain.model.LocalPokemon;
import com.app.poke.domain.model.Page;
import com.app.poke.domain.model.PageRequest;

/** Our own Pokémon store. */
public interface LocalPokemonGateway {

	/**
	 * Inserts a Pokémon without an id, or updates one with an id. The returned copy has its id and new version.
	 *
	 * @throws ConcurrentUpdateException if the stored version is no longer {@code pokemon.version()}, or if another
	 *         write stored the same Pokédex number first
	 * @throws com.app.poke.domain.exception.PokemonNameTakenException if another local Pokémon has the name, in any case
	 */
	LocalPokemon save(LocalPokemon pokemon);

	/** @return whether there was such a Pokémon to delete */
	boolean deleteById(long id);

	Optional<LocalPokemon> findById(long id);

	Optional<LocalPokemon> findByPokedexNumber(int pokedexNumber);

	/** In Pokédex order; Pokémon created locally (no number) come last, by id. */
	Page<LocalPokemon> findAll(PageRequest request);
}
