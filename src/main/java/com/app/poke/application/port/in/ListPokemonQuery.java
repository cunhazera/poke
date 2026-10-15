package com.app.poke.application.port.in;

import java.util.Objects;

import com.app.poke.domain.model.PageRequest;

public record ListPokemonQuery(PageRequest pageRequest) {

	public ListPokemonQuery {
		Objects.requireNonNull(pageRequest, "pageRequest");
	}

	public static ListPokemonQuery of(int page, int size) {
		return new ListPokemonQuery(new PageRequest(page, size));
	}
}
