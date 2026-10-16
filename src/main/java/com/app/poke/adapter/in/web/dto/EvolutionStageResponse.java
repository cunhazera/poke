package com.app.poke.adapter.in.web.dto;

import java.util.List;

/** One node of an evolution chain; {@code evolvesTo} is empty for the last stage. */
public record EvolutionStageResponse(int id, String name, String spriteUrl, List<EvolutionStageResponse> evolvesTo) {

	public EvolutionStageResponse {
		evolvesTo = List.copyOf(evolvesTo);
	}
}
