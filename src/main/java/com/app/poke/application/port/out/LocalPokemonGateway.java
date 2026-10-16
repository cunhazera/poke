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
	 */
	LocalPokemon save(LocalPokemon pokemon);

	Optional<LocalPokemon> findById(long id);

	Optional<LocalPokemon> findByPokedexNumber(int pokedexNumber);

	/** In Pokédex order; Pokémon created locally (no number) come last, by id. */
	Page<LocalPokemon> findAll(PageRequest request);
}
