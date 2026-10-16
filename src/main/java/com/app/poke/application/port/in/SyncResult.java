package com.app.poke.application.port.in;

import java.util.Objects;

import com.app.poke.domain.model.LocalPokemon;

/** @param created true for a first sync (a new local copy), false for a refresh of an existing one */
public record SyncResult(LocalPokemon pokemon, boolean created) {

	public SyncResult {
		Objects.requireNonNull(pokemon, "pokemon");
	}
}
