package com.app.poke.application.usecase;

import java.util.Objects;

import com.app.poke.application.port.in.CreateLocalPokemonCommand;
import com.app.poke.application.port.in.CreateLocalPokemonUseCase;
import com.app.poke.application.port.out.LocalPokemonGateway;
import com.app.poke.domain.model.LocalPokemon;

public final class CreateLocalPokemonInteractor implements CreateLocalPokemonUseCase {

	private final LocalPokemonGateway store;

	public CreateLocalPokemonInteractor(LocalPokemonGateway store) {
		this.store = Objects.requireNonNull(store, "store");
	}

	@Override
	public LocalPokemon create(CreateLocalPokemonCommand command) {
		return store.save(LocalPokemon.createdLocally(command.entry(), command.proprietary()));
	}
}
