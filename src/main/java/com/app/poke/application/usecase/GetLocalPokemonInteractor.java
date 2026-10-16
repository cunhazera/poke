package com.app.poke.application.usecase;

import java.util.Objects;

import com.app.poke.application.port.in.GetLocalPokemonUseCase;
import com.app.poke.application.port.out.LocalPokemonGateway;
import com.app.poke.domain.exception.LocalPokemonNotFoundException;
import com.app.poke.domain.model.LocalPokemon;

public final class GetLocalPokemonInteractor implements GetLocalPokemonUseCase {

	private final LocalPokemonGateway store;

	public GetLocalPokemonInteractor(LocalPokemonGateway store) {
		this.store = Objects.requireNonNull(store, "store");
	}

	@Override
	public LocalPokemon get(long id) {
		return store.findById(id).orElseThrow(() -> new LocalPokemonNotFoundException(id));
	}
}
