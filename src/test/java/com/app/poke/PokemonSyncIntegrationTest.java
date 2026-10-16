package com.app.poke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.app.poke.support.FakePokeApiServer;
import com.app.poke.support.TestcontainersConfiguration;

/** US03 end to end: an admin syncs from the (fake) PokeAPI into PostgreSQL; our proprietary data survives re-syncs. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PokemonSyncIntegrationTest {

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

	private long syncAsAdmin(int number, int expectedStatus) throws Exception {
		var body = mvc.perform(post("/api/v1/local-pokemon/sync/" + number).header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
				.andExpect(status().is(expectedStatus))
				.andReturn().getResponse().getContentAsString();
		return Long.parseLong(body.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
	}

	/** US04 will do this through the API; for now our data is written straight to the store. */
	private void enrich(long id) {
		db.update("UPDATE pokemon SET region = 'Kanto', habitat = 'grassland', latitude = 35.6762, longitude = 139.6503 WHERE id = ?", id);
		db.update("INSERT INTO pokemon_localized_name VALUES (?, 'pt-BR', 'Bulbassauro'), (?, 'ja', 'フシギダネ')", id, id);
		db.update("INSERT INTO pokemon_tag VALUES (?, 'starter')", id);
	}

	@Test
	void aFirstSyncStoresTheCatalogEntryWithItsChildRows() throws Exception {
		mvc.perform(post("/api/v1/local-pokemon/sync/1").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
				.andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.pokedexNumber").value(1))
				.andExpect(jsonPath("$.name").value("bulbasaur"))
				.andExpect(jsonPath("$.category").value("Seed Pokémon"))
				.andExpect(jsonPath("$.types[0]").value("grass"))
				.andExpect(jsonPath("$.stats.total").value(318))
				.andExpect(jsonPath("$.tags").isEmpty());

		assertThat(db.queryForObject("SELECT count(*) FROM pokemon_type", Integer.class)).isEqualTo(2);
		assertThat(db.queryForObject("SELECT count(*) FROM pokemon_ability", Integer.class)).isEqualTo(2);
		assertThat(db.queryForObject("SELECT synced_at IS NOT NULL FROM pokemon", Boolean.class)).isTrue();
	}

	@Test
	void aReSyncRefreshesTheCatalogDataAndKeepsOurProprietaryData() throws Exception {
		var id = syncAsAdmin(1, 201);
		enrich(id);
		API.stub("/pokemon-species/1", 200, FakePokeApiServer.fixture("species-1.json")
				.replace("While it is young", "Freshly synced: while it is young"));
		cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());

		syncAsAdmin(1, 200);

		mvc.perform(get("/api/v1/local-pokemon/" + id).header(HttpHeaders.AUTHORIZATION, bearer("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.startsWith("Freshly synced")))
				.andExpect(jsonPath("$.region").value("Kanto"))
				.andExpect(jsonPath("$.habitat").value("grassland"))
				.andExpect(jsonPath("$.coordinates.latitude").value(35.6762))
				.andExpect(jsonPath("$.localizedNames.pt-BR").value("Bulbassauro"))
				.andExpect(jsonPath("$.tags[0]").value("starter"))
				.andExpect(jsonPath("$.version").value(1));
		assertThat(db.queryForObject("SELECT count(*) FROM pokemon", Integer.class)).isEqualTo(1);
	}

	@Test
	void aReSyncDuringAnOutageChangesNothing() throws Exception {
		var id = syncAsAdmin(1, 201);
		enrich(id);
		var before = db.queryForMap("SELECT description, synced_at, version FROM pokemon WHERE id = ?", id);
		API.stub("/pokemon/1", 500, "{}");
		cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());

		mvc.perform(post("/api/v1/local-pokemon/sync/1").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
				.andExpect(status().isServiceUnavailable());

		assertThat(db.queryForMap("SELECT description, synced_at, version FROM pokemon WHERE id = ?", id)).isEqualTo(before);
		assertThat(db.queryForObject("SELECT count(*) FROM pokemon_tag", Integer.class)).isEqualTo(1);
	}

	@Test
	void anUnknownSpeciesIsNotFoundAndNothingIsStored() throws Exception {
		mvc.perform(post("/api/v1/local-pokemon/sync/1026").header(HttpHeaders.AUTHORIZATION, bearer("ADMIN")))
				.andExpect(status().isNotFound());

		assertThat(db.queryForObject("SELECT count(*) FROM pokemon", Integer.class)).isZero();
	}

	@Test
	void theLocalListShowsOnlyWhatWasSynced() throws Exception {
		syncAsAdmin(3, 201);
		syncAsAdmin(1, 201);

		mvc.perform(get("/api/v1/local-pokemon").header(HttpHeaders.AUTHORIZATION, bearer("USER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalItems").value(2))
				.andExpect(jsonPath("$.items[0].pokedexNumber").value(1))
				.andExpect(jsonPath("$.items[1].pokedexNumber").value(3));
	}

	@Test
	void onlyAnAdminCanSync() throws Exception {
		mvc.perform(post("/api/v1/local-pokemon/sync/1").header(HttpHeaders.AUTHORIZATION, bearer("USER")))
				.andExpect(status().isForbidden());

		assertThat(db.queryForObject("SELECT count(*) FROM pokemon", Integer.class)).isZero();
	}
}
