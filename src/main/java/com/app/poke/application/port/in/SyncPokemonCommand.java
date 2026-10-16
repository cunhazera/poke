package com.app.poke.application.port.in;

import java.util.Objects;

import com.app.poke.domain.model.PokedexNumber;

public record SyncPokemonCommand(PokedexNumber pokedexNumber) {

	public SyncPokemonCommand {
		Objects.requireNonNull(pokedexNumber, "pokedexNumber");
	}

	public static SyncPokemonCommand of(int pokedexNumber) {
		return new SyncPokemonCommand(new PokedexNumber(pokedexNumber));
	}
}
