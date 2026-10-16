package com.app.poke.adapter.in.web;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.app.poke.adapter.in.web.dto.AbilityRequest;
import com.app.poke.adapter.in.web.dto.AbilityResponse;
import com.app.poke.adapter.in.web.dto.BaseStatsResponse;
import com.app.poke.adapter.in.web.dto.CoordinatesRequest;
import com.app.poke.adapter.in.web.dto.CoordinatesResponse;
import com.app.poke.adapter.in.web.dto.CreateLocalPokemonRequest;
import com.app.poke.adapter.in.web.dto.LocalPokemonResponse;
import com.app.poke.adapter.in.web.dto.StatsRequest;
import com.app.poke.adapter.in.web.dto.UpdateLocalPokemonRequest;
import com.app.poke.application.port.in.CreateLocalPokemonCommand;
import com.app.poke.application.port.in.UpdateLocalPokemonCommand;
import com.app.poke.domain.exception.InvalidPokemonDataException;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.BaseStats;
import com.app.poke.domain.model.Coordinates;
import com.app.poke.domain.model.Height;
import com.app.poke.domain.model.LocalPokemon;
import com.app.poke.domain.model.PokedexEntry;
import com.app.poke.domain.model.ProprietaryData;
import com.app.poke.domain.model.Weight;

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

	/** Building the domain values applies their rules: a broken one is an {@link InvalidPokemonDataException} (400). */
	static CreateLocalPokemonCommand toCommand(CreateLocalPokemonRequest r) {
		return new CreateLocalPokemonCommand(
				entry(r.name(), r.category(), r.description(), r.heightM(), r.weightKg(), r.spriteUrl(), r.imageUrl(),
						r.types(), r.abilities(), r.stats()),
				proprietary(r.localizedNames(), r.region(), r.habitat(), r.coordinates(), r.tags()));
	}

	/** Without any catalog field the edit is proprietary only; with one, the catalog part must be complete. */
	static UpdateLocalPokemonCommand toCommand(long id, UpdateLocalPokemonRequest r) {
		var entry = r.hasCatalogFields() ? completeEntry(r) : null;
		return new UpdateLocalPokemonCommand(id, r.version(), entry,
				proprietary(r.localizedNames(), r.region(), r.habitat(), r.coordinates(), r.tags()));
	}

	private static PokedexEntry completeEntry(UpdateLocalPokemonRequest r) {
		required("name", r.name());
		required("heightM", r.heightM());
		required("weightKg", r.weightKg());
		required("types", r.types());
		required("abilities", r.abilities());
		required("stats", r.stats());
		return entry(r.name(), r.category(), r.description(), r.heightM(), r.weightKg(), r.spriteUrl(), r.imageUrl(),
				r.types(), r.abilities(), r.stats());
	}

	private static PokedexEntry entry(String name, String category, String description, BigDecimal heightM,
			BigDecimal weightKg, String spriteUrl, String imageUrl, List<String> types,
			List<AbilityRequest> abilities, StatsRequest stats) {
		return new PokedexEntry(name, category, description, Height.ofMetres(heightM), Weight.ofKilograms(weightKg),
				spriteUrl, imageUrl, types, abilities.stream().map(a -> new Ability(a.name(), a.hidden())).toList(),
				new BaseStats(stats.hp(), stats.attack(), stats.defense(), stats.specialAttack(), stats.specialDefense(),
						stats.speed()));
	}

	private static ProprietaryData proprietary(Map<String, String> localizedNames, String region, String habitat,
			CoordinatesRequest coordinates, List<String> tags) {
		return new ProprietaryData(localizedNames == null ? Map.of() : localizedNames, region, habitat,
				coordinates == null ? null : new Coordinates(coordinates.latitude(), coordinates.longitude()),
				tags == null ? Set.of() : new HashSet<>(tags));
	}

	private static void required(String field, Object value) {
		if (value == null) {
			throw new InvalidPokemonDataException(field, "%s is required when the catalog fields are sent".formatted(field));
		}
	}
}
