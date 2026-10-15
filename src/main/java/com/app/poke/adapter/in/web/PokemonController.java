package com.app.poke.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.poke.adapter.in.web.dto.PageResponse;
import com.app.poke.adapter.in.web.dto.PokemonSummaryResponse;
import com.app.poke.application.port.in.ListPokemonQuery;
import com.app.poke.application.port.in.ListPokemonUseCase;

@RestController
@RequestMapping("/api/v1/pokemon")
class PokemonController {

	private final ListPokemonUseCase listPokemon;

	PokemonController(ListPokemonUseCase listPokemon) {
		this.listPokemon = listPokemon;
	}

	/** US01: paginated listing. Range validation lives in the domain {@code PageRequest}. */
	@GetMapping
	PageResponse<PokemonSummaryResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		var result = listPokemon.list(ListPokemonQuery.of(page, size));
		return PokemonWebMapper.toPageResponse(result, PokemonWebMapper::toResponse);
	}
}
