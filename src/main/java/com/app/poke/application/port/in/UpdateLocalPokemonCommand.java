package com.app.poke.application.port.in;

import java.util.Objects;

import com.app.poke.domain.exception.InvalidPokemonDataException;
import com.app.poke.domain.model.PokedexEntry;
import com.app.poke.domain.model.ProprietaryData;

/**
 * A full replacement of a local Pokémon's editable data.
 *
 * @param version the version the editor read; a stale one is a conflict
 * @param entry the new catalog part, only for a Pokémon created locally; null for a synced one
 */
public record UpdateLocalPokemonCommand(long id, long version, PokedexEntry entry, ProprietaryData proprietary) {

	public UpdateLocalPokemonCommand {
		if (id < 1) {
			throw new IllegalArgumentException("id must be positive, was " + id);
		}
		if (version < 0) {
			throw new InvalidPokemonDataException("version", "version must not be negative, was " + version);
		}
		Objects.requireNonNull(proprietary, "proprietary");
	}
}
