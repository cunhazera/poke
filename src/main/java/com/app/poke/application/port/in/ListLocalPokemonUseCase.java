package com.app.poke.application.port.in;

import com.app.poke.domain.model.LocalPokemon;
import com.app.poke.domain.model.Page;

public interface ListLocalPokemonUseCase {

	Page<LocalPokemon> list(ListLocalPokemonQuery query);
}
