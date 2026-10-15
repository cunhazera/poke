package com.app.poke.adapter.out.pokeapi;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors of the PokeAPI JSON we consume. Only the fields we use are declared; everything else is ignored. */
final class PokeApiDtos {

	private PokeApiDtos() {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record NamedResource(String name, String url) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ResourceList(long count, List<NamedResource> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Pokemon(int id, String name, int weight, Sprites sprites, List<AbilitySlot> abilities) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Sprites(@JsonProperty("front_default") String frontDefault) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AbilitySlot(@JsonProperty("is_hidden") boolean hidden, int slot, NamedResource ability) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Species(List<Genus> genera) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Genus(String genus, NamedResource language) {
	}
}
