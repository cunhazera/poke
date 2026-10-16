package com.app.poke.application.port.in;

/** US04: removes a local Pokémon. A synced one can be synced again afterwards. */
public interface DeleteLocalPokemonUseCase {

	/** @throws com.app.poke.domain.exception.LocalPokemonNotFoundException if there is no such local Pokémon */
	void delete(long id);
}
