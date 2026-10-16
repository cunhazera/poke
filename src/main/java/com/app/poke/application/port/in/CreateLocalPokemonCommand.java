package com.app.poke.application.port.in;

import java.util.Objects;

import com.app.poke.domain.model.PokedexEntry;
import com.app.poke.domain.model.ProprietaryData;

public record CreateLocalPokemonCommand(PokedexEntry entry, ProprietaryData proprietary) {

	public CreateLocalPokemonCommand {
		Objects.requireNonNull(entry, "entry");
		Objects.requireNonNull(proprietary, "proprietary");
	}
}
