package com.app.poke.application.port.in;

/** US03: copies a Pokémon from the catalog into our store, or refreshes the copy, keeping our proprietary data. */
public interface SyncPokemonUseCase {

	/**
	 * @throws com.app.poke.domain.exception.PokemonNotFoundException if the catalog has no such species
	 * @throws com.app.poke.application.exception.ConcurrentUpdateException if the copy changed meanwhile
	 */
	SyncResult sync(SyncPokemonCommand command);
}
