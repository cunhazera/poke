package com.app.poke.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.in.GetPokemonDetailUseCase;
import com.app.poke.application.port.in.ListPokemonQuery;
import com.app.poke.application.port.in.ListPokemonUseCase;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.Page;
import com.app.poke.domain.model.PageRequest;
import com.app.poke.domain.model.PokemonSummary;
import com.app.poke.domain.model.Weight;
import com.app.poke.config.SecurityConfig;

/** Web slice: request binding, response mapping and error translation, with the use case stubbed. */
@WebMvcTest(PokemonController.class)
@Import(SecurityConfig.class)
class PokemonControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ListPokemonUseCase listPokemon;

	@MockitoBean
	private GetPokemonDetailUseCase getPokemonDetail;

	private final AtomicReference<ListPokemonQuery> received = new AtomicReference<>();

	private void respondWith(List<PokemonSummary> items, long total) {
		given(listPokemon.list(any())).willAnswer(invocation -> {
			ListPokemonQuery query = invocation.getArgument(0);
			received.set(query);
			return new Page<>(items, query.pageRequest(), total);
		});
	}

	@Test
	void mapsDomainPageToApiContract() throws Exception {
		respondWith(List.of(new PokemonSummary(1, "bulbasaur", "https://sprites/1.png", "Seed Pokémon",
				Weight.ofHectograms(69), List.of(new Ability("overgrow", false), new Ability("chlorophyll", true)))), 1025);

		mvc.perform(get("/api/v1/pokemon").param("page", "1").param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().json("""
						{
						  "items": [{
						    "id": 1, "name": "bulbasaur", "spriteUrl": "https://sprites/1.png",
						    "category": "Seed Pokémon", "weightKg": 6.9,
						    "abilities": [ { "name": "overgrow", "hidden": false }, { "name": "chlorophyll", "hidden": true } ]
						  }],
						  "page": 1, "size": 1, "totalItems": 1025, "totalPages": 1025,
						  "hasNext": true, "hasPrevious": true
						}
						""", org.springframework.test.json.JsonCompareMode.STRICT));
	}

	@Test
	void appliesDefaultPagination() throws Exception {
		respondWith(List.of(), 0);

		mvc.perform(get("/api/v1/pokemon")).andExpect(status().isOk());

		assertThat(received.get().pageRequest()).isEqualTo(new PageRequest(0, 20));
	}

	@Test
	void nullCategoryAndSpriteAreSerializedAsNull() throws Exception {
		respondWith(List.of(new PokemonSummary(1, "missingno", null, null, Weight.ofHectograms(0), List.of())), 1);

		mvc.perform(get("/api/v1/pokemon"))
				.andExpect(jsonPath("$.items[0].category").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.items[0].spriteUrl").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$.items[0].abilities").isEmpty());
	}

	@Test
	void passesTheSearchToTheUseCase() throws Exception {
		respondWith(List.of(), 0);

		mvc.perform(get("/api/v1/pokemon").param("name", "Mr. Mime").param("page", "0").param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(0))
				.andExpect(jsonPath("$.totalPages").value(0))
				.andExpect(jsonPath("$.items").isEmpty());

		assertThat(received.get().name()).hasValueSatisfying(name -> assertThat(name.slug()).isEqualTo("mr-mime"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void blankSearchIsNoSearch(String name) throws Exception {
		respondWith(List.of(), 0);

		mvc.perform(get("/api/v1/pokemon").param("name", name)).andExpect(status().isOk());

		assertThat(received.get().name()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "...", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" })
	void invalidSearchIsBadRequest(String name) throws Exception {
		mvc.perform(get("/api/v1/pokemon").param("name", name))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Invalid search"));

		verifyNoInteractions(listPokemon);
	}

	@Test
	void repeatedSearchIsBadRequest() throws Exception {
		mvc.perform(get("/api/v1/pokemon?name=char&name=pika")).andExpect(status().isBadRequest());

		verifyNoInteractions(listPokemon);
	}

	@ParameterizedTest
	@ValueSource(strings = { "size=0", "size=51", "page=-1" })
	void outOfRangePaginationIsBadRequest(String query) throws Exception {
		mvc.perform(get("/api/v1/pokemon?" + query))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(query.split("=")[0])));

		verifyNoInteractions(listPokemon);
	}

	@ParameterizedTest
	@ValueSource(strings = { "page=abc", "size=1.5", "size=99999999999" })
	void nonNumericPaginationIsBadRequest(String query) throws Exception {
		mvc.perform(get("/api/v1/pokemon?" + query))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));

		verifyNoInteractions(listPokemon);
	}

	@ParameterizedTest
	@ValueSource(strings = { "page=1&page=2", "size=5&size=10", "page=1&size=5&page=1" })
	void repeatedPaginationParameterIsBadRequestInsteadOfSilentlyUsingTheFirst(String query) throws Exception {
		mvc.perform(get("/api/v1/pokemon?" + query))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Invalid query parameter"))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("must not be repeated")));

		verifyNoInteractions(listPokemon);
	}

	@Test
	void catalogOutageIsServiceUnavailableWithoutLeakingInternals() throws Exception {
		given(listPokemon.list(any())).willThrow(new CatalogUnavailableException(
				"PokeAPI request failed: pokemon 1", new RuntimeException("Connection refused: localhost/127.0.0.1:443")));

		mvc.perform(get("/api/v1/pokemon"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(503))
				.andExpect(jsonPath("$.title").value("Pokémon catalog unavailable"))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("127.0.0.1"))));
	}

	@Test
	void unexpectedErrorIsInternalServerErrorWithGenericMessage() throws Exception {
		given(listPokemon.list(any())).willThrow(new IllegalStateException("secret internal detail"));

		mvc.perform(get("/api/v1/pokemon"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
	}
}
