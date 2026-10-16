package com.app.poke.application.usecase;

import java.util.Objects;

import com.app.poke.application.port.in.DeleteLocalPokemonUseCase;
import com.app.poke.application.port.out.LocalPokemonGateway;
import com.app.poke.domain.exception.LocalPokemonNotFoundException;

public final class DeleteLocalPokemonInteractor implements DeleteLocalPokemonUseCase {

	private final LocalPokemonGateway store;

	public DeleteLocalPokemonInteractor(LocalPokemonGateway store) {
		this.store = Objects.requireNonNull(store, "store");
	}

	@Override
	public void delete(long id) {
		if (!store.deleteById(id)) {
			throw new LocalPokemonNotFoundException(id);
		}
	}
}
