package com.app.poke.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.app.poke.adapter.in.web.dto.CreateLocalPokemonRequest;
import com.app.poke.adapter.in.web.dto.LocalPokemonResponse;
import com.app.poke.adapter.in.web.dto.PageResponse;
import com.app.poke.adapter.in.web.dto.UpdateLocalPokemonRequest;
import com.app.poke.application.port.in.CreateLocalPokemonUseCase;
import com.app.poke.application.port.in.DeleteLocalPokemonUseCase;
import com.app.poke.application.port.in.GetLocalPokemonUseCase;
import com.app.poke.application.port.in.ListLocalPokemonQuery;
import com.app.poke.application.port.in.ListLocalPokemonUseCase;
import com.app.poke.application.port.in.SyncPokemonCommand;
import com.app.poke.application.port.in.SyncPokemonUseCase;
import com.app.poke.application.port.in.UpdateLocalPokemonUseCase;
import com.app.poke.domain.exception.InvalidPokemonIdException;

import jakarta.validation.Valid;

/**
 * Our own Pokémon: copies synced from PokeAPI (US03) and full CRUD on them (US04). Writes need ADMIN, reading needs any
 * signed-in user (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/local-pokemon")
class LocalPokemonController {

	private final SyncPokemonUseCase syncPokemon;
	private final ListLocalPokemonUseCase listLocalPokemon;
	private final GetLocalPokemonUseCase getLocalPokemon;
	private final CreateLocalPokemonUseCase createLocalPokemon;
	private final UpdateLocalPokemonUseCase updateLocalPokemon;
	private final DeleteLocalPokemonUseCase deleteLocalPokemon;

	LocalPokemonController(SyncPokemonUseCase syncPokemon, ListLocalPokemonUseCase listLocalPokemon,
			GetLocalPokemonUseCase getLocalPokemon, CreateLocalPokemonUseCase createLocalPokemon,
			UpdateLocalPokemonUseCase updateLocalPokemon, DeleteLocalPokemonUseCase deleteLocalPokemon) {
		this.syncPokemon = syncPokemon;
		this.listLocalPokemon = listLocalPokemon;
		this.getLocalPokemon = getLocalPokemon;
		this.createLocalPokemon = createLocalPokemon;
		this.updateLocalPokemon = updateLocalPokemon;
		this.deleteLocalPokemon = deleteLocalPokemon;
	}

	/** 201 with {@code Location} for a first copy, 200 for a refresh of an existing one. */
	@PostMapping("/sync/{pokedexNumber}")
	ResponseEntity<LocalPokemonResponse> sync(@PathVariable String pokedexNumber) {
		var number = PathIds.asInt(pokedexNumber).orElseThrow(() -> new InvalidPokemonIdException(
				"Pokémon id must be a positive Pokédex number, was '%s'".formatted(pokedexNumber)));
		var result = syncPokemon.sync(SyncPokemonCommand.of(number));
		var body = LocalPokemonWebMapper.toResponse(result.pokemon());
		return result.created() ? created(body) : ResponseEntity.ok(body);
	}

	/** US04: a Pokémon that exists only in our store. */
	@PostMapping
	ResponseEntity<LocalPokemonResponse> create(@Valid @RequestBody CreateLocalPokemonRequest request) {
		var pokemon = createLocalPokemon.create(LocalPokemonWebMapper.toCommand(request));
		return created(LocalPokemonWebMapper.toResponse(pokemon));
	}

	/** US04: a full replacement of the editable data; the body carries the version it was read at. */
	@PutMapping("/{id}")
	LocalPokemonResponse update(@PathVariable String id, @Valid @RequestBody UpdateLocalPokemonRequest request) {
		var command = LocalPokemonWebMapper.toCommand(localId(id), request);
		return LocalPokemonWebMapper.toResponse(updateLocalPokemon.update(command));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable String id) {
		deleteLocalPokemon.delete(localId(id));
		return ResponseEntity.noContent().build();
	}

	@GetMapping
	PageResponse<LocalPokemonResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		var result = listLocalPokemon.list(ListLocalPokemonQuery.of(page, size));
		return PokemonWebMapper.toPageResponse(result, LocalPokemonWebMapper::toResponse);
	}

	@GetMapping("/{id}")
	LocalPokemonResponse get(@PathVariable String id) {
		return LocalPokemonWebMapper.toResponse(getLocalPokemon.get(localId(id)));
	}

	private static long localId(String id) {
		return PathIds.asLong(id).filter(n -> n > 0)
				.orElseThrow(() -> new InvalidPathVariableException("local Pokémon id", id));
	}

	private static ResponseEntity<LocalPokemonResponse> created(LocalPokemonResponse body) {
		var location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/v1/local-pokemon/{id}").buildAndExpand(body.id()).toUri();
		return ResponseEntity.created(location).body(body);
	}
}
