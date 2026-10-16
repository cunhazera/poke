package com.app.poke.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A Pokémon in our own store (US03): catalog data that sync refreshes, plus proprietary data that sync never touches.
 *
 * @param id the store's id; null until first stored
 * @param pokedexNumber the national Pokédex number, or null for a Pokémon created locally
 * @param syncedAt when the catalog data was last copied; null for a Pokémon created locally
 * @param version the stored version this copy was read at, for optimistic locking
 */
public record LocalPokemon(Long id, Integer pokedexNumber, PokedexEntry entry, ProprietaryData proprietary,
		Instant syncedAt, long version) {

	public LocalPokemon {
		Objects.requireNonNull(entry, "entry");
		Objects.requireNonNull(proprietary, "proprietary");
		if (pokedexNumber != null && pokedexNumber < 1) {
			throw new IllegalArgumentException("pokedexNumber must be positive, was " + pokedexNumber);
		}
		if (version < 0) {
			throw new IllegalArgumentException("version must not be negative, was " + version);
		}
	}

	/** The first copy of a catalog Pokémon: nothing proprietary yet. */
	public static LocalPokemon fromCatalog(PokedexNumber number, PokedexEntry entry, Instant syncedAt) {
		return new LocalPokemon(null, number.value(), entry, ProprietaryData.EMPTY, Objects.requireNonNull(syncedAt), 0);
	}

	/** A re-sync: the catalog part is replaced, the proprietary part is kept exactly as it is. */
	public LocalPokemon refreshedFrom(PokedexEntry freshEntry, Instant syncedAt) {
		if (pokedexNumber == null) {
			throw new IllegalStateException("Local Pokémon %d was created locally and has no catalog entry".formatted(id));
		}
		return new LocalPokemon(id, pokedexNumber, freshEntry, proprietary, Objects.requireNonNull(syncedAt), version);
	}
}
