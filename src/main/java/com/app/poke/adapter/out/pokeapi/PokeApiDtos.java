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
	record Pokemon(int id, String name, int height, int weight, Sprites sprites, List<TypeSlot> types,
			List<AbilitySlot> abilities, List<Stat> stats) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Sprites(@JsonProperty("front_default") String frontDefault, OtherSprites other) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record OtherSprites(@JsonProperty("official-artwork") Artwork officialArtwork) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Artwork(@JsonProperty("front_default") String frontDefault) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TypeSlot(int slot, NamedResource type) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AbilitySlot(@JsonProperty("is_hidden") boolean hidden, int slot, NamedResource ability) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Stat(@JsonProperty("base_stat") int baseStat, NamedResource stat) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Species(List<Genus> genera, @JsonProperty("flavor_text_entries") List<FlavorText> flavorTextEntries,
			@JsonProperty("evolution_chain") Resource evolutionChain) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Genus(String genus, NamedResource language) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record FlavorText(@JsonProperty("flavor_text") String text, NamedResource language) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Resource(String url) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record EvolutionChain(ChainLink chain) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ChainLink(NamedResource species, @JsonProperty("evolves_to") List<ChainLink> evolvesTo) {
	}
}
