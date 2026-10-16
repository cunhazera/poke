package com.app.poke.domain.exception;

/** An edit tried to change the catalog part of a synced Pokémon, which PokeAPI owns and every re-sync overwrites. */
public class CatalogFieldsNotEditableException extends RuntimeException {

	public CatalogFieldsNotEditableException(long id) {
		super(("Local Pokémon %d is a copy of a PokeAPI Pokémon: its catalog fields (name, measurements, images, types, "
				+ "abilities, stats, texts) belong to PokeAPI. Edit only the proprietary fields, or re-sync it.").formatted(id));
	}
}
