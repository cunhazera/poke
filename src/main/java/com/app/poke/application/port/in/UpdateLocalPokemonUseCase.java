package com.app.poke.application.port.in;

import com.app.poke.domain.model.LocalPokemon;

/** US04: edits a local Pokémon. A synced copy accepts only proprietary changes; a local one is replaced whole. */
public interface UpdateLocalPokemonUseCase {

	/**
	 * @throws com.app.poke.domain.exception.LocalPokemonNotFoundException if there is no such local Pokémon
	 * @throws com.app.poke.domain.exception.CatalogFieldsNotEditableException if a synced copy's catalog part would change
	 * @throws com.app.poke.application.exception.ConcurrentUpdateException if the version is stale
	 * @throws com.app.poke.domain.exception.PokemonNameTakenException if another local Pokémon has the new name
	 */
	LocalPokemon update(UpdateLocalPokemonCommand command);
}
