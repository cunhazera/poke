package com.app.poke.application.port.in;

import com.app.poke.domain.model.PokemonDetail;

/** US02: everything about one Pokémon, including its whole evolution chain. */
public interface GetPokemonDetailUseCase {

	/** @throws com.app.poke.domain.exception.PokemonNotFoundException if no species has this Pokédex number */
	PokemonDetail get(GetPokemonDetailQuery query);
}
