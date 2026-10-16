package com.app.poke.application.usecase;

import java.util.Objects;

import com.app.poke.application.port.in.UpdateLocalPokemonCommand;
import com.app.poke.application.port.in.UpdateLocalPokemonUseCase;
import com.app.poke.application.port.out.LocalPokemonGateway;
import com.app.poke.domain.exception.LocalPokemonNotFoundException;
import com.app.poke.domain.model.LocalPokemon;

/** Who may change what is the domain's rule ({@link LocalPokemon#edited}); the store rejects a stale version. */
public final class UpdateLocalPokemonInteractor implements UpdateLocalPokemonUseCase {

	private final LocalPokemonGateway store;

	public UpdateLocalPokemonInteractor(LocalPokemonGateway store) {
		this.store = Objects.requireNonNull(store, "store");
	}

	@Override
	public LocalPokemon update(UpdateLocalPokemonCommand command) {
		var stored = store.findById(command.id()).orElseThrow(() -> new LocalPokemonNotFoundException(command.id()));
		return store.save(stored.edited(command.entry(), command.proprietary(), command.version()));
	}
}
