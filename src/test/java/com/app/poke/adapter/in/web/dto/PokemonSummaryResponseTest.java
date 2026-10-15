package com.app.poke.adapter.in.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PokemonSummaryResponseTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	/** Serialize then re-parse, so assertions compare what clients actually receive. */
	private JsonNode wireJson(Object value) {
		return mapper.readTree(mapper.writeValueAsString(value));
	}

	private static PokemonSummaryResponse bulbasaur() {
		return new PokemonSummaryResponse(
				1,
				"bulbasaur",
				"https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1.png",
				"Seed Pokémon",
				6.9,
				List.of(new AbilityResponse("overgrow", false), new AbilityResponse("chlorophyll", true)));
	}

	@Test
	void serializesPokemonSummaryWithUs01Fields() {
		var expected = mapper.readTree("""
				{
				  "id": 1,
				  "name": "bulbasaur",
				  "spriteUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1.png",
				  "category": "Seed Pokémon",
				  "weightKg": 6.9,
				  "abilities": [
				    { "name": "overgrow", "hidden": false },
				    { "name": "chlorophyll", "hidden": true }
				  ]
				}
				""");

		assertThat(wireJson(bulbasaur())).isEqualTo(expected);
	}

	@Test
	void serializesPageWithPaginationMetadata() {
		var page = new PageResponse<>(List.of(bulbasaur()), 0, 20, 1025, 52);

		var expected = mapper.readTree("""
				{
				  "items": [ %s ],
				  "page": 0,
				  "size": 20,
				  "totalItems": 1025,
				  "totalPages": 52,
				  "hasNext": true,
				  "hasPrevious": false
				}
				""".formatted(mapper.writeValueAsString(bulbasaur())));

		assertThat(wireJson(page)).isEqualTo(expected);
	}

	@Test
	void lastPageHasNoNext() {
		var page = new PageResponse<>(List.of(), 51, 20, 1025, 52);

		assertThat(page.hasNext()).isFalse();
		assertThat(page.hasPrevious()).isTrue();
	}

	@Test
	void abilitiesAreDefensivelyCopied() {
		var abilities = new ArrayList<>(List.of(new AbilityResponse("overgrow", false)));
		var response = new PokemonSummaryResponse(1, "bulbasaur", null, "Seed Pokémon", 6.9, abilities);

		abilities.add(new AbilityResponse("chlorophyll", true));

		assertThat(response.abilities()).hasSize(1);
	}
}
