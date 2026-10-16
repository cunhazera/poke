package com.app.poke.adapter.in.web;

import java.util.List;

import com.app.poke.adapter.in.web.dto.AbilityResponse;
import com.app.poke.adapter.in.web.dto.BaseStatsResponse;
import com.app.poke.adapter.in.web.dto.CoordinatesResponse;
import com.app.poke.adapter.in.web.dto.LocalPokemonResponse;
import com.app.poke.domain.model.LocalPokemon;

final class LocalPokemonWebMapper {

	private LocalPokemonWebMapper() {
	}

	static LocalPokemonResponse toResponse(LocalPokemon pokemon) {
		var entry = pokemon.entry();
		var stats = entry.stats();
		var proprietary = pokemon.proprietary();
		var at = proprietary.coordinates();
		return new LocalPokemonResponse(pokemon.id(), pokemon.pokedexNumber(), entry.name(), entry.category(),
				entry.description(), entry.height().metres(), entry.weight().kilograms(), entry.spriteUrl(),
				entry.imageUrl(), entry.types(),
				entry.abilities().stream().map(a -> new AbilityResponse(a.name(), a.hidden())).toList(),
				new BaseStatsResponse(stats.hp(), stats.attack(), stats.defense(), stats.specialAttack(),
						stats.specialDefense(), stats.speed(), stats.total()),
				proprietary.localizedNames(), proprietary.region(), proprietary.habitat(),
				at == null ? null : new CoordinatesResponse(at.latitude(), at.longitude()),
				List.copyOf(proprietary.tags()), pokemon.syncedAt(), pokemon.version());
	}
}
