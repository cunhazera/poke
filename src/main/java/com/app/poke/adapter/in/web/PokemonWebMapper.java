package com.app.poke.adapter.in.web;

import java.util.function.Function;

import com.app.poke.adapter.in.web.dto.AbilityResponse;
import com.app.poke.adapter.in.web.dto.PageResponse;
import com.app.poke.adapter.in.web.dto.PokemonSummaryResponse;
import com.app.poke.domain.model.Page;
import com.app.poke.domain.model.PokemonSummary;

final class PokemonWebMapper {

	private PokemonWebMapper() {
	}

	static PokemonSummaryResponse toResponse(PokemonSummary summary) {
		var abilities = summary.abilities().stream().map(a -> new AbilityResponse(a.name(), a.hidden())).toList();
		return new PokemonSummaryResponse(summary.id(), summary.name(), summary.spriteUrl(), summary.category(),
				summary.weight().kilograms(), abilities);
	}

	static <T, R> PageResponse<R> toPageResponse(Page<T> page, Function<T, R> itemMapper) {
		return new PageResponse<>(page.items().stream().map(itemMapper).toList(), page.request().page(),
				page.request().size(), page.totalItems(), page.totalPages());
	}
}
