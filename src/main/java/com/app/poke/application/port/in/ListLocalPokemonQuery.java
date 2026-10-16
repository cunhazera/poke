package com.app.poke.application.port.in;

import java.util.Objects;
import java.util.Optional;

import com.app.poke.domain.model.NameQuery;
import com.app.poke.domain.model.PageRequest;

/**
 * @param search if present, only Pokémon whose name or a localized name contains it are listed
 */
public record ListLocalPokemonQuery(PageRequest pageRequest, Optional<NameQuery> search) {

	public ListLocalPokemonQuery {
		Objects.requireNonNull(pageRequest, "pageRequest");
		search = search == null ? Optional.empty() : search;
	}

	public static ListLocalPokemonQuery of(int page, int size) {
		return of(page, size, null);
	}

	/** @param search what the user typed; null or blank means no search */
	public static ListLocalPokemonQuery of(int page, int size, String search) {
		var query = search == null || search.isBlank() ? Optional.<NameQuery>empty() : Optional.of(new NameQuery(search));
		return new ListLocalPokemonQuery(new PageRequest(page, size), query);
	}
}
