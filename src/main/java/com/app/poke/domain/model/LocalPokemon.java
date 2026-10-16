package com.app.poke.domain.model;

import java.time.Instant;
import java.util.Objects;

import com.app.poke.domain.exception.CatalogFieldsNotEditableException;
import com.app.poke.domain.exception.InvalidPokemonDataException;

/**
 * A Pokémon in our own store: catalog data that sync refreshes (US03), plus proprietary data that sync never touches.
 * Editing (US04) follows who owns what: on a synced copy only the proprietary part may change; a Pokémon created
 * locally has no upstream owner, so everything may.
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

	/** A Pokémon that exists only in our store: no Pokédex number, never synced. */
	public static LocalPokemon createdLocally(PokedexEntry entry, ProprietaryData proprietary) {
		return new LocalPokemon(null, null, entry, proprietary, null, 0);
	}

	public boolean isSynced() {
		return pokedexNumber != null;
	}

	/**
	 * A full replacement of what may be edited.
	 *
	 * @param newEntry the new catalog part for a Pokémon created locally; must be null for a synced one
	 * @param expectedVersion the version the editor read, so the store rejects an edit made on stale data
	 * @throws CatalogFieldsNotEditableException if a synced Pokémon's catalog part would change
	 */
	public LocalPokemon edited(PokedexEntry newEntry, ProprietaryData newProprietary, long expectedVersion) {
		if (id == null) {
			throw new IllegalStateException("Only a stored Pokémon can be edited");
		}
		if (isSynced() && newEntry != null) {
			throw new CatalogFieldsNotEditableException(id);
		}
		if (!isSynced() && newEntry == null) {
			throw new InvalidPokemonDataException("name",
					"Local Pokémon %d was created locally: send all its fields, the catalog ones included".formatted(id));
		}
		return new LocalPokemon(id, pokedexNumber, isSynced() ? entry : newEntry, newProprietary, syncedAt, expectedVersion);
	}
}
