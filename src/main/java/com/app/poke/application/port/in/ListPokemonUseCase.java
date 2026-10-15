package com.app.poke.application.port.in;

import com.app.poke.domain.model.Page;
import com.app.poke.domain.model.PokemonSummary;

/** US01: browse Pokémon page by page. */
public interface ListPokemonUseCase {

	Page<PokemonSummary> list(ListPokemonQuery query);
}
