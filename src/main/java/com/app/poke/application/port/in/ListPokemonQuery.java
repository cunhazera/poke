package com.app.poke.application.port.in;

import java.util.Objects;
import java.util.Optional;

import com.app.poke.domain.model.NameQuery;
import com.app.poke.domain.model.PageRequest;

/**
 * @param name the search, if any: only Pokémon whose name contains it are listed
 */
public record ListPokemonQuery(PageRequest pageRequest, Optional<NameQuery> name) {

	public ListPokemonQuery {
		Objects.requireNonNull(pageRequest, "pageRequest");
		name = name == null ? Optional.empty() : name;
	}

	public static ListPokemonQuery of(int page, int size) {
		return of(page, size, null);
	}

	/** @param name what the user typed; null or blank means no search */
	public static ListPokemonQuery of(int page, int size, String name) {
		var search = name == null || name.isBlank() ? Optional.<NameQuery>empty() : Optional.of(new NameQuery(name));
		return new ListPokemonQuery(new PageRequest(page, size), search);
	}
}
