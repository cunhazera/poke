package com.app.poke.application.usecase;

import java.util.Objects;

import com.app.poke.application.port.in.ListLocalPokemonQuery;
import com.app.poke.application.port.in.ListLocalPokemonUseCase;
import com.app.poke.application.port.out.LocalPokemonGateway;
import com.app.poke.domain.model.LocalPokemon;
import com.app.poke.domain.model.Page;

public final class ListLocalPokemonInteractor implements ListLocalPokemonUseCase {

	private final LocalPokemonGateway store;

	public ListLocalPokemonInteractor(LocalPokemonGateway store) {
		this.store = Objects.requireNonNull(store, "store");
	}

	@Override
	public Page<LocalPokemon> list(ListLocalPokemonQuery query) {
		return store.findAll(query.pageRequest());
	}
}
