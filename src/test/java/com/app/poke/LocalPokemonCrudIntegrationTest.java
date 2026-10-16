package com.app.poke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.app.poke.support.FakePokeApiServer;
import com.app.poke.support.TestcontainersConfiguration;

import tools.jackson.databind.json.JsonMapper;

/** US04 end to end: real JWTs, the real store on PostgreSQL, and syncs from the (fake) PokeAPI. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LocalPokemonCrudIntegrationTest {

	private static final FakePokeApiServer API = FakePokeApiServer.start();
	private static final JsonMapper JSON = new JsonMapper();

	private static final String KANTO_EDIT = """
			{ "version": %d, "localizedNames": { "pt-BR": "Bulbassauro" }, "region": "Kanto", "habitat": "grassland",
			  "coordinates": { "latitude": 35.6762, "longitude": 139.6503 }, "tags": ["starter"] }""";

	private static final String MISSINGNO = """
			{ "name": "missingno", "category": "Glitch Pokémon", "heightM": 1.0, "weightKg": 1590.8,
			  "types": ["normal"], "abilities": [ { "name": "pressure", "hidden": false } ],
			  "stats": { "hp": 33, "attack": 136, "defense": 1, "specialAttack": 6, "specialDefense": 6, "speed": 29 },
			  "tags": ["glitch"] }""";

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
	private JdbcTemplate db;

	@Autowired
	private JwtEncoder encoder;

	@Autowired
	private CacheManager cacheManager;

	@BeforeEach
	void emptyStoreAndCaches() {
		db.execute("TRUNCATE pokemon RESTART IDENTITY CASCADE");
		cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
		API.stubBulbasaurLine();
	}

	private String bearer(String role) {
		var now = Instant.now();
		var claims = JwtClaimsSet.builder().issuer("poke").subject("1").issuedAt(now).expiresAt(now.plusSeconds(300))
				.claim("roles", List.of(role)).build();
		return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	private ResultActions asAdmin(MockHttpServletRequestBuilder request) throws Exception {
		return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static long idOf(ResultActions result) throws Exception {
		return JSON.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
	}

	@Test
	void anEditOfOurDataSurvivesAReSync() throws Exception {
		var id = idOf(asAdmin(post("/api/v1/local-pokemon/sync/1")).andExpect(status().isCreated()));

		asAdmin(json(put("/api/v1/local-pokemon/" + id), KANTO_EDIT.formatted(0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.region").value("Kanto"))
				.andExpect(jsonPath("$.version").value(1));
		asAdmin(post("/api/v1/local-pokemon/sync/1")).andExpect(status().isOk());

		asAdmin(get("/api/v1/local-pokemon/" + id))
				.andExpect(jsonPath("$.name").value("bulbasaur"))
				.andExpect(jsonPath("$.localizedNames.pt-BR").value("Bulbassauro"))
				.andExpect(jsonPath("$.coordinates.latitude").value(35.6762))
				.andExpect(jsonPath("$.tags[0]").value("starter"))
				.andExpect(jsonPath("$.version").value(2));
	}

	@Test
	void twoEditorsCannotOverwriteEachOther() throws Exception {
		var id = idOf(asAdmin(post("/api/v1/local-pokemon/sync/1")));

		asAdmin(json(put("/api/v1/local-pokemon/" + id), KANTO_EDIT.formatted(0))).andExpect(status().isOk());
		asAdmin(json(put("/api/v1/local-pokemon/" + id), KANTO_EDIT.formatted(0).replace("Kanto", "Johto")))
				.andExpect(status().isConflict());

		assertThat(db.queryForObject("SELECT region FROM pokemon WHERE id = ?", String.class, id)).isEqualTo("Kanto");
	}

	@Test
	void theCatalogPartOfASyncedPokemonIsRefusedAndNothingChanges() throws Exception {
		var id = idOf(asAdmin(post("/api/v1/local-pokemon/sync/1")));

		asAdmin(json(put("/api/v1/local-pokemon/" + id), MISSINGNO.replaceFirst("\\{", "{ \"version\": 0,").replaceFirst("missingno", "bulbasaur")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Catalog fields not editable"));

		assertThat(db.queryForObject("SELECT category FROM pokemon WHERE id = ?", String.class, id)).isEqualTo("Seed Pokémon");
	}

	@Test
	void aLocalPokemonLivesThroughTheWholeCrudCycle() throws Exception {
		var id = idOf(asAdmin(json(post("/api/v1/local-pokemon"), MISSINGNO))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.pokedexNumber").doesNotExist())
				.andExpect(jsonPath("$.weightKg").value(1590.8))
				.andExpect(jsonPath("$.stats.total").value(211)));

		asAdmin(json(put("/api/v1/local-pokemon/" + id),
				MISSINGNO.replaceFirst("\\{", "{ \"version\": 0,").replace("\"normal\"", "\"normal\", \"bird\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.field").value("types"));
		asAdmin(json(put("/api/v1/local-pokemon/" + id),
				MISSINGNO.replaceFirst("\\{", "{ \"version\": 0,").replace("\"normal\"", "\"normal\", \"flying\"")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.types[1]").value("flying"));

		mvc.perform(get("/api/v1/local-pokemon/" + id).header(HttpHeaders.AUTHORIZATION, bearer("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("missingno"));

		asAdmin(delete("/api/v1/local-pokemon/" + id)).andExpect(status().isNoContent());
		asAdmin(get("/api/v1/local-pokemon/" + id)).andExpect(status().isNotFound());
		asAdmin(delete("/api/v1/local-pokemon/" + id)).andExpect(status().isNotFound());
		assertThat(db.queryForObject("SELECT count(*) FROM pokemon_tag", Integer.class)).isZero();
	}

	@Test
	void aDeletedCopyCanBeSyncedAgain() throws Exception {
		var first = idOf(asAdmin(post("/api/v1/local-pokemon/sync/1")));

		asAdmin(delete("/api/v1/local-pokemon/" + first)).andExpect(status().isNoContent());

		asAdmin(post("/api/v1/local-pokemon/sync/1")).andExpect(status().isCreated());
	}

	@Test
	void namesAreUniqueAcrossSyncedAndLocalPokemon() throws Exception {
		asAdmin(post("/api/v1/local-pokemon/sync/1"));

		asAdmin(json(post("/api/v1/local-pokemon"), MISSINGNO.replace("missingno", "bulbasaur")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("Name taken"));
	}

	@Test
	void aPlainUserCanReadButNotWrite() throws Exception {
		var user = bearer("USER");

		mvc.perform(json(post("/api/v1/local-pokemon"), MISSINGNO).header(HttpHeaders.AUTHORIZATION, user))
				.andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/local-pokemon").header(HttpHeaders.AUTHORIZATION, user)).andExpect(status().isOk());
		assertThat(db.queryForObject("SELECT count(*) FROM pokemon", Integer.class)).isZero();
	}
}
