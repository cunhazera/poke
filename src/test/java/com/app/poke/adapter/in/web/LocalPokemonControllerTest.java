package com.app.poke.adapter.in.web;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.exception.ConcurrentUpdateException;
import com.app.poke.application.port.in.GetLocalPokemonUseCase;
import com.app.poke.application.port.in.ListLocalPokemonQuery;
import com.app.poke.application.port.in.ListLocalPokemonUseCase;
import com.app.poke.application.port.in.SyncPokemonCommand;
import com.app.poke.application.port.in.SyncPokemonUseCase;
import com.app.poke.application.port.in.SyncResult;
import com.app.poke.config.SecurityConfig;
import com.app.poke.domain.exception.LocalPokemonNotFoundException;
import com.app.poke.domain.exception.PokemonNotFoundException;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.BaseStats;
import com.app.poke.domain.model.Coordinates;
import com.app.poke.domain.model.Height;
import com.app.poke.domain.model.LocalPokemon;
import com.app.poke.domain.model.Page;
import com.app.poke.domain.model.PageRequest;
import com.app.poke.domain.model.PokedexEntry;
import com.app.poke.domain.model.ProprietaryData;
import com.app.poke.domain.model.Weight;

/** Web slice for US03 with the real security rules (roles included); the use cases are stubbed. */
@WebMvcTest(LocalPokemonController.class)
@Import(SecurityConfig.class)
class LocalPokemonControllerTest {

	private static final LocalPokemon PIKACHU = new LocalPokemon(7L, 25,
			new PokedexEntry("pikachu", "Mouse Pokémon", "It stores electricity.", Height.ofDecimetres(4),
					Weight.ofHectograms(60), "https://sprites/25.png", "https://art/25.png", List.of("electric"),
					List.of(new Ability("static", false), new Ability("lightning-rod", true)),
					new BaseStats(35, 55, 40, 50, 50, 90)),
			new ProprietaryData(Map.of("pt-BR", "Pikachu", "ja", "ピカチュウ"), "Kanto", "forest",
					new Coordinates(new BigDecimal("35.676200"), new BigDecimal("139.650300")), Set.of("starter", "mascot")),
			Instant.parse("2026-10-15T12:00:00Z"), 2);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private SyncPokemonUseCase syncPokemon;

	@MockitoBean
	private ListLocalPokemonUseCase listLocalPokemon;

	@MockitoBean
	private GetLocalPokemonUseCase getLocalPokemon;

	private static RequestPostProcessor admin() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	private static RequestPostProcessor user() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Nested
	class Sync {

		@Test
		void aFirstSyncIsCreatedWithItsLocation() throws Exception {
			given(syncPokemon.sync(SyncPokemonCommand.of(25))).willReturn(new SyncResult(PIKACHU, true));

			mvc.perform(post("/api/v1/local-pokemon/sync/25").with(admin()))
					.andExpect(status().isCreated())
					.andExpect(header().string("Location", endsWith("/api/v1/local-pokemon/7")))
					.andExpect(jsonPath("$.id").value(7));
		}

		@Test
		void aReSyncIsOkWithoutALocation() throws Exception {
			given(syncPokemon.sync(any())).willReturn(new SyncResult(PIKACHU, false));

			mvc.perform(post("/api/v1/local-pokemon/sync/25").with(admin()))
					.andExpect(status().isOk())
					.andExpect(header().doesNotExist("Location"));
		}

		@Test
		void aPlainUserIsForbidden() throws Exception {
			mvc.perform(post("/api/v1/local-pokemon/sync/25").with(user()))
					.andExpect(status().isForbidden())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Access denied"));

			verifyNoInteractions(syncPokemon);
		}

		@Test
		void anonymousIsUnauthorized() throws Exception {
			mvc.perform(post("/api/v1/local-pokemon/sync/25")).andExpect(status().isUnauthorized());

			verifyNoInteractions(syncPokemon);
		}

		@ParameterizedTest
		@ValueSource(strings = { "0", "abc", "+1", "99999999999" })
		void anInvalidNumberIsBadRequest(String number) throws Exception {
			mvc.perform(post("/api/v1/local-pokemon/sync/" + number).with(admin()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").value("Invalid Pokémon id"));

			verifyNoInteractions(syncPokemon);
		}

		@Test
		void anUnknownSpeciesIsNotFound() throws Exception {
			given(syncPokemon.sync(any())).willThrow(new PokemonNotFoundException(10001));

			mvc.perform(post("/api/v1/local-pokemon/sync/10001").with(admin())).andExpect(status().isNotFound());
		}

		@Test
		void aConcurrentSyncIsAConflict() throws Exception {
			given(syncPokemon.sync(any())).willThrow(new ConcurrentUpdateException("Pokémon #25 was stored concurrently"));

			mvc.perform(post("/api/v1/local-pokemon/sync/25").with(admin()))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.title").value("Concurrent update"));
		}

		@Test
		void anUpstreamOutageIsServiceUnavailable() throws Exception {
			given(syncPokemon.sync(any())).willThrow(new CatalogUnavailableException("down", null));

			mvc.perform(post("/api/v1/local-pokemon/sync/25").with(admin())).andExpect(status().isServiceUnavailable());
		}
	}

	@Nested
	class Read {

		@Test
		void mapsALocalPokemonToTheApiContract() throws Exception {
			given(getLocalPokemon.get(7)).willReturn(PIKACHU);

			mvc.perform(get("/api/v1/local-pokemon/7").with(user()))
					.andExpect(status().isOk())
					.andExpect(content().json("""
							{
							  "id": 7, "pokedexNumber": 25, "name": "pikachu", "category": "Mouse Pokémon",
							  "description": "It stores electricity.", "heightM": 0.4, "weightKg": 6.0,
							  "spriteUrl": "https://sprites/25.png", "imageUrl": "https://art/25.png",
							  "types": ["electric"],
							  "abilities": [ { "name": "static", "hidden": false }, { "name": "lightning-rod", "hidden": true } ],
							  "stats": { "hp": 35, "attack": 55, "defense": 40, "specialAttack": 50, "specialDefense": 50,
							             "speed": 90, "total": 320 },
							  "localizedNames": { "ja": "ピカチュウ", "pt-BR": "Pikachu" },
							  "region": "Kanto", "habitat": "forest",
							  "coordinates": { "latitude": 35.676200, "longitude": 139.650300 },
							  "tags": ["mascot", "starter"],
							  "syncedAt": "2026-10-15T12:00:00Z",
							  "version": 2
							}
							""", JsonCompareMode.STRICT));
		}

		@Test
		void aPokemonWithoutProprietaryDataHasEmptyCollectionsAndNullFields() throws Exception {
			var bare = new LocalPokemon(8L, null, PIKACHU.entry(), ProprietaryData.EMPTY, null, 0);
			given(getLocalPokemon.get(8)).willReturn(bare);

			mvc.perform(get("/api/v1/local-pokemon/8").with(user()))
					.andExpect(jsonPath("$.pokedexNumber").value(org.hamcrest.Matchers.nullValue()))
					.andExpect(jsonPath("$.localizedNames").isEmpty())
					.andExpect(jsonPath("$.tags").isEmpty())
					.andExpect(jsonPath("$.coordinates").value(org.hamcrest.Matchers.nullValue()))
					.andExpect(jsonPath("$.syncedAt").value(org.hamcrest.Matchers.nullValue()));
		}

		@Test
		void listsAPageInTheSharedEnvelope() throws Exception {
			given(listLocalPokemon.list(ListLocalPokemonQuery.of(0, 20)))
					.willReturn(new Page<>(List.of(PIKACHU), new PageRequest(0, 20), 1));

			mvc.perform(get("/api/v1/local-pokemon").with(user()))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items[0].name").value("pikachu"))
					.andExpect(jsonPath("$.totalItems").value(1))
					.andExpect(jsonPath("$.hasNext").value(false));
		}

		@Test
		void invalidPaginationIsBadRequest() throws Exception {
			mvc.perform(get("/api/v1/local-pokemon?size=51").with(user())).andExpect(status().isBadRequest());

			verifyNoInteractions(listLocalPokemon);
		}

		@Test
		void readingNeedsASignedInUser() throws Exception {
			mvc.perform(get("/api/v1/local-pokemon")).andExpect(status().isUnauthorized());
			mvc.perform(get("/api/v1/local-pokemon/7")).andExpect(status().isUnauthorized());

			verifyNoInteractions(listLocalPokemon, getLocalPokemon);
		}

		@ParameterizedTest
		@ValueSource(strings = { "0", "abc", "-1", "1.5", "1234567890123456789" })
		void anInvalidIdIsBadRequest(String id) throws Exception {
			mvc.perform(get("/api/v1/local-pokemon/" + id).with(user()))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").value("Invalid id"));

			verifyNoInteractions(getLocalPokemon);
		}

		@Test
		void anUnknownIdIsNotFound() throws Exception {
			given(getLocalPokemon.get(404)).willThrow(new LocalPokemonNotFoundException(404));

			mvc.perform(get("/api/v1/local-pokemon/404").with(user()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.title").value("Local Pokémon not found"));
			verify(getLocalPokemon).get(404);
		}
	}
}
