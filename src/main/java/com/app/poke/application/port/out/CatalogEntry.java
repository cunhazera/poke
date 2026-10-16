package com.app.poke.application.port.out;

import java.util.Objects;

/** One species in the catalog's index: enough to search and page without loading its details. */
public record CatalogEntry(int id, String name) {

	public CatalogEntry {
		Objects.requireNonNull(name, "name");
	}
}
