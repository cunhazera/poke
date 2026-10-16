package com.app.poke.application.port.in;

import com.app.poke.domain.model.LocalPokemon;

public interface GetLocalPokemonUseCase {

	/** @throws com.app.poke.domain.exception.LocalPokemonNotFoundException if there is no such local Pokémon */
	LocalPokemon get(long id);
}
