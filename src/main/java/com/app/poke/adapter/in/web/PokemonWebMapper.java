package com.app.poke.adapter.in.web;

import java.util.List;
import java.util.function.Function;

import com.app.poke.adapter.in.web.dto.AbilityResponse;
import com.app.poke.adapter.in.web.dto.BaseStatsResponse;
import com.app.poke.adapter.in.web.dto.EvolutionStageResponse;
import com.app.poke.adapter.in.web.dto.PageResponse;
import com.app.poke.adapter.in.web.dto.PokemonDetailResponse;
import com.app.poke.adapter.in.web.dto.PokemonSummaryResponse;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.EvolutionStage;
import com.app.poke.domain.model.Page;
import com.app.poke.domain.model.PokemonDetail;
import com.app.poke.domain.model.PokemonSummary;

final class PokemonWebMapper {

	private PokemonWebMapper() {
	}

	static PokemonSummaryResponse toResponse(PokemonSummary summary) {
		return new PokemonSummaryResponse(summary.id(), summary.name(), summary.spriteUrl(), summary.category(),
				summary.weight().kilograms(), toResponses(summary.abilities()));
	}

	static PokemonDetailResponse toResponse(PokemonDetail detail) {
		var stats = detail.stats();
		return new PokemonDetailResponse(detail.id(), detail.name(), detail.category(), detail.imageUrl(),
				detail.spriteUrl(), detail.description(), detail.height().metres(), detail.weight().kilograms(),
				detail.types(), toResponses(detail.abilities()),
				new BaseStatsResponse(stats.hp(), stats.attack(), stats.defense(), stats.specialAttack(),
						stats.specialDefense(), stats.speed(), stats.total()),
				toResponse(detail.evolutionChain()));
	}

	private static EvolutionStageResponse toResponse(EvolutionStage stage) {
		return new EvolutionStageResponse(stage.id(), stage.name(), stage.spriteUrl(),
				stage.evolvesTo().stream().map(PokemonWebMapper::toResponse).toList());
	}

	private static List<AbilityResponse> toResponses(List<Ability> abilities) {
		return abilities.stream().map(a -> new AbilityResponse(a.name(), a.hidden())).toList();
	}

	static <T, R> PageResponse<R> toPageResponse(Page<T> page, Function<T, R> itemMapper) {
		return new PageResponse<>(page.items().stream().map(itemMapper).toList(), page.request().page(),
				page.request().size(), page.totalItems(), page.totalPages());
	}
}
