package com.app.poke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.app.poke.support.FakePokeApiServer;
import com.app.poke.support.TestcontainersConfiguration;

/** End to end: HTTP request → controller → interactor → PokeAPI adapter → (fake) PokeAPI, with real wiring and caching. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class PokemonListingIntegrationTest {

	private static final FakePokeApiServer API = FakePokeApiServer.start();

	@DynamicPropertySource
	static void pokeApi(DynamicPropertyRegistry registry) {
		registry.add("pokeapi.base-url", API::baseUrl);
		registry.add("pokeapi.read-timeout", () -> "1s");
	}

	@AfterAll
	static void stopApi() {
		API.close();
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CacheManager cacheManager;

	@BeforeEach
	void clearCaches() {
		cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
	}

	@Test
	void listsAPageCombiningPokemonAndSpeciesData() throws Exception {
		API.stubFirstThree("offset=0&limit=3");

		mvc.perform(get("/api/v1/pokemon").param("size", "3"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "items": [
						    { "id": 1, "name": "bulbasaur", "category": "Seed Pokémon", "weightKg": 6.9,
						      "spriteUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1.png",
						      "abilities": [ { "name": "overgrow", "hidden": false }, { "name": "chlorophyll", "hidden": true } ] },
						    { "id": 2, "name": "ivysaur", "category": "Seed Pokémon", "weightKg": 13.0 },
						    { "id": 3, "name": "venusaur", "category": "Seed Pokémon", "weightKg": 100.0 }
						  ],
						  "page": 0, "size": 3, "totalItems": 1025, "totalPages": 342,
						  "hasNext": true, "hasPrevious": false
						}
						""", JsonCompareMode.LENIENT));
	}

	@Test
	void repeatedRequestsAreServedFromCache() throws Exception {
		API.stubFirstThree("offset=0&limit=3");
		var before = API.totalHits();
		mvc.perform(get("/api/v1/pokemon").param("size", "3")).andExpect(status().isOk());
		var hitsAfterFirst = API.totalHits();

		mvc.perform(get("/api/v1/pokemon").param("size", "3")).andExpect(status().isOk());

		assertThat(hitsAfterFirst).as("1 list + 3 pokemon + 3 species").isEqualTo(before + 7);
		assertThat(API.totalHits()).as("second request must not reach PokeAPI").isEqualTo(hitsAfterFirst);
	}

	@Test
	void invalidPaginationNeverReachesPokeApi() throws Exception {
		var before = API.totalHits();

		mvc.perform(get("/api/v1/pokemon").param("size", "500"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("size")));

		assertThat(API.totalHits()).isEqualTo(before);
	}

	@Test
	void upstreamOutageIsServiceUnavailableAndIsNotCached() throws Exception {
		API.stub("/pokemon-species?offset=0&limit=3", 500, "{\"message\":\"boom\"}");

		mvc.perform(get("/api/v1/pokemon").param("size", "3"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Pokémon catalog unavailable"));

		API.stubFirstThree("offset=0&limit=3");
		mvc.perform(get("/api/v1/pokemon").param("size", "3"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(3));
	}

	@Test
	void slowUpstreamTimesOutInsteadOfHanging() throws Exception {
		API.stub("/pokemon-species?offset=0&limit=3", 200, FakePokeApiServer.fixture("species-list-0-3.json"),
				Duration.ofSeconds(3));

		mvc.perform(get("/api/v1/pokemon").param("size", "3"))
				.andExpect(status().isServiceUnavailable());
	}
}
