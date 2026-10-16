package com.app.poke.application.port.in;

import com.app.poke.domain.model.LocalPokemon;

/** US04: stores a Pokémon that exists only in our store (no Pokédex number, never synced). */
public interface CreateLocalPokemonUseCase {

	/** @throws com.app.poke.domain.exception.PokemonNameTakenException if another local Pokémon has the name */
	LocalPokemon create(CreateLocalPokemonCommand command);
}
