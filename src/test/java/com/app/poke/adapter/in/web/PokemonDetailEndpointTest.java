package com.app.poke.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.in.GetPokemonDetailQuery;
import com.app.poke.application.port.in.GetPokemonDetailUseCase;
import com.app.poke.application.port.in.ListPokemonUseCase;
import com.app.poke.domain.exception.PokemonNotFoundException;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.BaseStats;
import com.app.poke.domain.model.EvolutionStage;
import com.app.poke.domain.model.Height;
import com.app.poke.domain.model.PokemonDetail;
import com.app.poke.domain.model.Weight;
import com.app.poke.config.SecurityConfig;

/** Web slice for US02: path binding, the JSON contract and error translation, with the use case stubbed. */
@WebMvcTest(PokemonController.class)
@Import(SecurityConfig.class)
class PokemonDetailEndpointTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private GetPokemonDetailUseCase getPokemonDetail;

	@MockitoBean
	private ListPokemonUseCase listPokemon;

	private static final PokemonDetail EEVEE = new PokemonDetail(133, "eevee", "Evolution Pokémon", "https://art/133.png",
			"https://sprites/133.png", "Its genetic code is irregular.", Height.ofDecimetres(3), Weight.ofHectograms(65),
			List.of("normal"),
			List.of(new Ability("run-away", false), new Ability("adaptability", false), new Ability("anticipation", true)),
			new BaseStats(55, 55, 50, 45, 65, 55),
			new EvolutionStage(133, "eevee", "https://sprites/133.png", List.of(
					new EvolutionStage(134, "vaporeon", "https://sprites/134.png", List.of()),
					new EvolutionStage(135, "jolteon", null, List.of()))));

	@Test
	void mapsTheDomainDetailToTheApiContract() throws Exception {
		given(getPokemonDetail.get(GetPokemonDetailQuery.of(133))).willReturn(EEVEE);

		mvc.perform(get("/api/v1/pokemon/133"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().json("""
						{
						  "id": 133, "name": "eevee", "category": "Evolution Pokémon",
						  "imageUrl": "https://art/133.png", "spriteUrl": "https://sprites/133.png",
						  "description": "Its genetic code is irregular.",
						  "heightM": 0.3, "weightKg": 6.5,
						  "types": ["normal"],
						  "abilities": [ { "name": "run-away", "hidden": false }, { "name": "adaptability", "hidden": false },
						                 { "name": "anticipation", "hidden": true } ],
						  "stats": { "hp": 55, "attack": 55, "defense": 50, "specialAttack": 45, "specialDefense": 65,
						             "speed": 55, "total": 325 },
						  "evolutionChain": {
						    "id": 133, "name": "eevee", "spriteUrl": "https://sprites/133.png",
						    "evolvesTo": [
						      { "id": 134, "name": "vaporeon", "spriteUrl": "https://sprites/134.png", "evolvesTo": [] },
						      { "id": 135, "name": "jolteon", "spriteUrl": null, "evolvesTo": [] }
						    ]
						  }
						}
						""", JsonCompareMode.STRICT));
	}

	@Test
	void missingOptionalTextsAreSerializedAsNullNotOmitted() throws Exception {
		var bare = new PokemonDetail(128, "tauros", null, null, null, null, Height.ofDecimetres(14),
				Weight.ofHectograms(884), List.of("normal"), List.of(), new BaseStats(75, 100, 95, 40, 70, 110),
				new EvolutionStage(128, "tauros", null, List.of()));
		given(getPokemonDetail.get(any())).willReturn(bare);

		mvc.perform(get("/api/v1/pokemon/128"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.category").value(nullValue()))
				.andExpect(jsonPath("$.description").value(nullValue()))
				.andExpect(jsonPath("$.imageUrl").value(nullValue()))
				.andExpect(jsonPath("$.evolutionChain.evolvesTo").isEmpty());
	}

	@Test
	void passesThePathIdToTheUseCase() throws Exception {
		given(getPokemonDetail.get(any())).willReturn(EEVEE);

		mvc.perform(get("/api/v1/pokemon/133")).andExpect(status().isOk());

		verify(getPokemonDetail).get(GetPokemonDetailQuery.of(133));
	}

	@ParameterizedTest(name = "/api/v1/pokemon/{0}")
	@ValueSource(strings = { "0", "-1", "abc", "1.5", "99999999999", "1e3", "+1", "0x1F" })
	void invalidIdIsBadRequestAndNeverReachesTheUseCase(String id) throws Exception {
		mvc.perform(get("/api/v1/pokemon/" + id))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Invalid Pokémon id"));

		verifyNoInteractions(getPokemonDetail);
	}

	@Test
	void aUrlRejectedByTheSecurityFirewallIsStillAProblemDetail() throws Exception {
		mvc.perform(get("/api/v1/pokemon/%20"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Invalid request"));

		verifyNoInteractions(getPokemonDetail);
	}

	@Test
	void unknownPokemonIsNotFound() throws Exception {
		given(getPokemonDetail.get(any())).willThrow(new PokemonNotFoundException(99999));

		mvc.perform(get("/api/v1/pokemon/99999"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Pokémon not found"))
				.andExpect(jsonPath("$.detail").value(containsString("99999")));
	}

	@Test
	void catalogOutageIsServiceUnavailableWithoutLeakingInternals() throws Exception {
		given(getPokemonDetail.get(any())).willThrow(new CatalogUnavailableException(
				"PokeAPI request failed: evolution chain 67", new RuntimeException("Connection refused: 10.0.0.5:443")));

		mvc.perform(get("/api/v1/pokemon/133"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Pokémon catalog unavailable"))
				.andExpect(content().string(not(containsString("10.0.0.5"))))
				.andExpect(content().string(not(containsString("evolution chain 67"))));
	}

	@Test
	void unexpectedErrorIsAGenericInternalError() throws Exception {
		given(getPokemonDetail.get(any())).willThrow(new IllegalStateException("secret internal detail"));

		mvc.perform(get("/api/v1/pokemon/133"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().string(not(containsString("secret"))));
	}

	@Test
	void trailingSlashIsNotAnAlias() throws Exception {
		mvc.perform(get("/api/v1/pokemon/133/")).andExpect(status().isNotFound());

		verifyNoInteractions(getPokemonDetail);
	}

	@Test
	void writesNeedAuthenticationBeforeAnythingElse() throws Exception {
		mvc.perform(post("/api/v1/pokemon/133"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		verifyNoInteractions(getPokemonDetail);
	}

	@Test
	void anAuthenticatedWriteIsStillNotAllowed() throws Exception {
		mvc.perform(post("/api/v1/pokemon/133").with(jwt())).andExpect(status().isMethodNotAllowed());

		verifyNoInteractions(getPokemonDetail);
	}

	/** MockMvc does not strip HEAD bodies like a servlet container does; the empty body is checked by Newman. */
	@Test
	void headIsRoutedLikeGet() throws Exception {
		given(getPokemonDetail.get(any())).willReturn(EEVEE);

		mvc.perform(head("/api/v1/pokemon/133"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
	}
}
