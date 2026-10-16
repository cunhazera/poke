package com.app.poke.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.app.poke.adapter.in.web.dto.LocalPokemonResponse;
import com.app.poke.adapter.in.web.dto.PageResponse;
import com.app.poke.application.port.in.GetLocalPokemonUseCase;
import com.app.poke.application.port.in.ListLocalPokemonQuery;
import com.app.poke.application.port.in.ListLocalPokemonUseCase;
import com.app.poke.application.port.in.SyncPokemonCommand;
import com.app.poke.application.port.in.SyncPokemonUseCase;
import com.app.poke.domain.exception.InvalidPokemonIdException;

/** US03: our own copies of Pokémon. Sync needs ADMIN, reading needs any signed-in user (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/local-pokemon")
class LocalPokemonController {

	private final SyncPokemonUseCase syncPokemon;
	private final ListLocalPokemonUseCase listLocalPokemon;
	private final GetLocalPokemonUseCase getLocalPokemon;

	LocalPokemonController(SyncPokemonUseCase syncPokemon, ListLocalPokemonUseCase listLocalPokemon,
			GetLocalPokemonUseCase getLocalPokemon) {
		this.syncPokemon = syncPokemon;
		this.listLocalPokemon = listLocalPokemon;
		this.getLocalPokemon = getLocalPokemon;
	}

	/** 201 with {@code Location} for a first copy, 200 for a refresh of an existing one. */
	@PostMapping("/sync/{pokedexNumber}")
	ResponseEntity<LocalPokemonResponse> sync(@PathVariable String pokedexNumber) {
		var number = PathIds.asInt(pokedexNumber).orElseThrow(() -> new InvalidPokemonIdException(
				"Pokémon id must be a positive Pokédex number, was '%s'".formatted(pokedexNumber)));
		var result = syncPokemon.sync(SyncPokemonCommand.of(number));
		var body = LocalPokemonWebMapper.toResponse(result.pokemon());
		if (!result.created()) {
			return ResponseEntity.ok(body);
		}
		var location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/v1/local-pokemon/{id}").buildAndExpand(body.id()).toUri();
		return ResponseEntity.created(location).body(body);
	}

	@GetMapping
	PageResponse<LocalPokemonResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		var result = listLocalPokemon.list(ListLocalPokemonQuery.of(page, size));
		return PokemonWebMapper.toPageResponse(result, LocalPokemonWebMapper::toResponse);
	}

	@GetMapping("/{id}")
	LocalPokemonResponse get(@PathVariable String id) {
		var localId = PathIds.asLong(id).filter(n -> n > 0)
				.orElseThrow(() -> new InvalidPathVariableException("local Pokémon id", id));
		return LocalPokemonWebMapper.toResponse(getLocalPokemon.get(localId));
	}
}
