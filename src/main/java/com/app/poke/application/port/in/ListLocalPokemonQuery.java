package com.app.poke.application.port.in;

import java.util.Objects;

import com.app.poke.domain.model.PageRequest;

public record ListLocalPokemonQuery(PageRequest pageRequest) {

	public ListLocalPokemonQuery {
		Objects.requireNonNull(pageRequest, "pageRequest");
	}

	public static ListLocalPokemonQuery of(int page, int size) {
		return new ListLocalPokemonQuery(new PageRequest(page, size));
	}
}
