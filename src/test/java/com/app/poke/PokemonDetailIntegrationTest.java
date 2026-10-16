package com.app.poke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import com.app.poke.support.FakePokeApiServer;
import com.app.poke.support.TestcontainersConfiguration;

/** End to end for US02: HTTP → controller → interactor → PokeAPI adapter → (fake) PokeAPI, with real wiring and caching. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PokemonDetailIntegrationTest {

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
	void showsAMiddleStageWithItsWholeChainFromRealPayloads() throws Exception {
		API.stubBulbasaurLine();

		mvc.perform(get("/api/v1/pokemon/2"))
				.andExpect(status().isOk())
				.andExpect(content().json("""
						{
						  "id": 2, "name": "ivysaur", "category": "Seed Pokémon",
						  "imageUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/2.png",
						  "spriteUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/2.png",
						  "description": "Exposure to sunlight adds to its strength. Sunlight also makes the bud on its back grow larger.",
						  "heightM": 1.0, "weightKg": 13.0, "types": ["grass", "poison"],
						  "stats": { "hp": 60, "attack": 62, "defense": 63, "specialAttack": 80, "specialDefense": 80,
						             "speed": 60, "total": 405 },
						  "evolutionChain": {
						    "id": 1, "name": "bulbasaur",
						    "spriteUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1.png",
						    "evolvesTo": [ { "id": 2, "name": "ivysaur", "evolvesTo": [
						      { "id": 3, "name": "venusaur", "evolvesTo": [],
						        "spriteUrl": "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/3.png" } ] } ]
						  }
						}
						""", JsonCompareMode.LENIENT));
	}

	@Test
	void repeatedDetailIsServedFromCache() throws Exception {
		API.stubBulbasaurLine();
		var before = API.totalHits();
		mvc.perform(get("/api/v1/pokemon/2")).andExpect(status().isOk());
		var afterFirst = API.totalHits();

		mvc.perform(get("/api/v1/pokemon/2")).andExpect(status().isOk());

		assertThat(afterFirst).as("pokemon 1-3 + species 2 + chain 1").isEqualTo(before + 5);
		assertThat(API.totalHits()).as("second request must not reach PokeAPI").isEqualTo(afterFirst);
	}

	@Test
	void theListingWarmsTheCacheForTheDetailView() throws Exception {
		API.stubFirstThree("offset=0&limit=3");
		mvc.perform(get("/api/v1/pokemon").param("size", "3")).andExpect(status().isOk());
		var afterListing = API.totalHits();
		var chainHitsBefore = API.hits("/evolution-chain/1");

		mvc.perform(get("/api/v1/pokemon/2")).andExpect(status().isOk());

		assertThat(API.totalHits()).as("only the evolution chain is new").isEqualTo(afterListing + 1);
		assertThat(API.hits("/evolution-chain/1")).isEqualTo(chainHitsBefore + 1);
	}

	@Test
	void unknownPokemonIsNotFound() throws Exception {
		mvc.perform(get("/api/v1/pokemon/99999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Pokémon not found"));
	}

	@Test
	void anAlternateFormIsNotFoundBecauseItHasNoSpecies() throws Exception {
		API.stub("/pokemon/10001", 200, FakePokeApiServer.fixture("pokemon-1.json").replace("\"id\": 1,", "\"id\": 10001,"));

		mvc.perform(get("/api/v1/pokemon/10001")).andExpect(status().isNotFound());
	}

	@Test
	void invalidIdNeverReachesPokeApi() throws Exception {
		var before = API.totalHits();

		mvc.perform(get("/api/v1/pokemon/0")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/pokemon/abc")).andExpect(status().isBadRequest());

		assertThat(API.totalHits()).isEqualTo(before);
	}

	@Test
	void evolutionChainOutageIsServiceUnavailableAndIsNotCached() throws Exception {
		API.stubBulbasaurLine();
		API.stub("/evolution-chain/1", 500, "{\"message\":\"boom\"}");

		mvc.perform(get("/api/v1/pokemon/2"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Pokémon catalog unavailable"));

		API.stubBulbasaurLine();
		mvc.perform(get("/api/v1/pokemon/2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.evolutionChain.id").value(1));
	}
}
