package com.app.poke.adapter.in.web;

import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.poke.adapter.in.web.dto.PageResponse;
import com.app.poke.adapter.in.web.dto.PokemonDetailResponse;
import com.app.poke.adapter.in.web.dto.PokemonSummaryResponse;
import com.app.poke.application.port.in.GetPokemonDetailQuery;
import com.app.poke.application.port.in.GetPokemonDetailUseCase;
import com.app.poke.application.port.in.ListPokemonQuery;
import com.app.poke.application.port.in.ListPokemonUseCase;
import com.app.poke.domain.exception.InvalidPokemonIdException;

@RestController
@RequestMapping("/api/v1/pokemon")
class PokemonController {

	/** Plain decimal digits only: rejects "+1", "1e3", "0x1F" and padding that Integer.parseInt would accept. */
	private static final Pattern DIGITS = Pattern.compile("[0-9]{1,10}");

	private final ListPokemonUseCase listPokemon;
	private final GetPokemonDetailUseCase getPokemonDetail;

	PokemonController(ListPokemonUseCase listPokemon, GetPokemonDetailUseCase getPokemonDetail) {
		this.listPokemon = listPokemon;
		this.getPokemonDetail = getPokemonDetail;
	}

	/** US01: paginated listing. Range validation lives in the domain {@code PageRequest}. */
	@GetMapping
	PageResponse<PokemonSummaryResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		var result = listPokemon.list(ListPokemonQuery.of(page, size));
		return PokemonWebMapper.toPageResponse(result, PokemonWebMapper::toResponse);
	}

	/** US02: detail view. The id is bound as text so every malformed id gets the same 400 as a non-positive one. */
	@GetMapping("/{id}")
	PokemonDetailResponse detail(@PathVariable String id) {
		var detail = getPokemonDetail.get(GetPokemonDetailQuery.of(parseId(id)));
		return PokemonWebMapper.toResponse(detail);
	}

	private static int parseId(String id) {
		if (!DIGITS.matcher(id).matches()) {
			throw new InvalidPokemonIdException("Pokémon id must be a positive Pokédex number, was '%s'".formatted(id));
		}
		try {
			return Integer.parseInt(id);
		} catch (NumberFormatException e) { // ten digits can still overflow an int
			throw new InvalidPokemonIdException("Pokémon id is out of range: '%s'".formatted(id));
		}
	}
}
