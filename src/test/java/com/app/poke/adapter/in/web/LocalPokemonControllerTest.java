package com.app.poke.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.mockito.ArgumentCaptor;
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
import com.app.poke.application.port.in.CreateLocalPokemonCommand;
import com.app.poke.application.port.in.CreateLocalPokemonUseCase;
import com.app.poke.application.port.in.DeleteLocalPokemonUseCase;
import com.app.poke.application.port.in.GetLocalPokemonUseCase;
import com.app.poke.application.port.in.ListLocalPokemonQuery;
import com.app.poke.application.port.in.ListLocalPokemonUseCase;
import com.app.poke.application.port.in.SyncPokemonCommand;
import com.app.poke.application.port.in.SyncPokemonUseCase;
import com.app.poke.application.port.in.SyncResult;
import com.app.poke.application.port.in.UpdateLocalPokemonCommand;
import com.app.poke.application.port.in.UpdateLocalPokemonUseCase;
import com.app.poke.config.SecurityConfig;
import com.app.poke.domain.exception.CatalogFieldsNotEditableException;
import com.app.poke.domain.exception.LocalPokemonNotFoundException;
import com.app.poke.domain.exception.PokemonNameTakenException;
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

/** Web slice for US03 and US04 with the real security rules (roles included); the use cases are stubbed. */
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

	@MockitoBean
	private CreateLocalPokemonUseCase createLocalPokemon;

	@MockitoBean
	private UpdateLocalPokemonUseCase updateLocalPokemon;

	@MockitoBean
	private DeleteLocalPokemonUseCase deleteLocalPokemon;

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

	private static final String NEW_POKEMON = """
			{
			  "name": "missingno", "category": "Glitch Pokémon", "description": "A glitch.",
			  "heightM": 1.0, "weightKg": 1590.8, "spriteUrl": null, "imageUrl": "https://art/missingno.png",
			  "types": ["normal", "flying"],
			  "abilities": [ { "name": "pressure", "hidden": false } ],
			  "stats": { "hp": 33, "attack": 136, "defense": 1, "specialAttack": 6, "specialDefense": 6, "speed": 29 },
			  "localizedNames": { "ja": "けつばん" }, "region": "Kanto", "habitat": "sea",
			  "coordinates": { "latitude": -10.5, "longitude": 20.25 }, "tags": ["glitch", "glitch"]
			}
			""";

	private static final String PROPRIETARY_EDIT = """
			{
			  "version": 2,
			  "localizedNames": { "ja": "ピカチュウ", "pt-BR": "Pikachu" }, "region": "Kanto", "habitat": "forest",
			  "coordinates": { "latitude": 35.6762, "longitude": 139.6503 }, "tags": ["mascot"]
			}
			""";

	@Nested
	class Create {

		@Test
		void mapsTheBodyToTheCommandAndAnswersCreatedWithItsLocation() throws Exception {
			given(createLocalPokemon.create(any())).willReturn(PIKACHU);

			mvc.perform(post("/api/v1/local-pokemon").with(admin()).contentType(MediaType.APPLICATION_JSON).content(NEW_POKEMON))
					.andExpect(status().isCreated())
					.andExpect(header().string("Location", endsWith("/api/v1/local-pokemon/7")))
					.andExpect(jsonPath("$.id").value(7));

			var command = ArgumentCaptor.forClass(CreateLocalPokemonCommand.class);
			verify(createLocalPokemon).create(command.capture());
			var entry = command.getValue().entry();
			assertThat(entry.name()).isEqualTo("missingno");
			assertThat(entry.height().decimetres()).isEqualTo(10);
			assertThat(entry.weight().hectograms()).isEqualTo(15908);
			assertThat(entry.types()).containsExactly("normal", "flying");
			assertThat(entry.abilities()).containsExactly(new Ability("pressure", false));
			assertThat(entry.stats()).isEqualTo(new BaseStats(33, 136, 1, 6, 6, 29));
			assertThat(entry.spriteUrl()).isNull();
			var proprietary = command.getValue().proprietary();
			assertThat(proprietary.localizedNames()).containsEntry("ja", "けつばん");
			assertThat(proprietary.coordinates()).isEqualTo(new Coordinates(new BigDecimal("-10.5"), new BigDecimal("20.25")));
			assertThat(proprietary.tags()).containsExactly("glitch");
		}

		@Test
		void theProprietaryFieldsAreOptional() throws Exception {
			given(createLocalPokemon.create(any())).willReturn(PIKACHU);

			mvc.perform(post("/api/v1/local-pokemon").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
					{ "name": "missingno", "heightM": 1, "weightKg": 2, "types": ["normal"], "abilities": [],
					  "stats": { "hp": 1, "attack": 1, "defense": 1, "specialAttack": 1, "specialDefense": 1, "speed": 1 } }
					""")).andExpect(status().isCreated());

			var command = ArgumentCaptor.forClass(CreateLocalPokemonCommand.class);
			verify(createLocalPokemon).create(command.capture());
			assertThat(command.getValue().proprietary()).isEqualTo(ProprietaryData.EMPTY);
		}

		@Test
		void missingRequiredFieldsAreListed() throws Exception {
			mvc.perform(post("/api/v1/local-pokemon").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
					{ "name": "missingno", "types": ["normal", null], "stats": { "hp": 1 } }
					"""))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").value("Invalid request body"))
					.andExpect(jsonPath("$.errors.heightM").exists())
					.andExpect(jsonPath("$.errors.weightKg").exists())
					.andExpect(jsonPath("$.errors.abilities").exists())
					.andExpect(jsonPath("$['errors']['stats.attack']").exists())
					.andExpect(jsonPath("$['errors']['types[1]']").exists());

			verifyNoInteractions(createLocalPokemon);
		}

		@ParameterizedTest
		@ValueSource(strings = {
				"\"heightM\": 0.25", "\"types\": [\"fyre\"]", "\"name\": \"Missing No\"", "\"spriteUrl\": \"javascript:x\"",
				"\"tags\": [\"Bad Tag\"]", "\"localizedNames\": { \"Japanese\": \"x\" }",
				"\"coordinates\": { \"latitude\": 91, \"longitude\": 0 }" })
		void aBrokenDomainRuleIsBadRequestNamingTheField(String override) throws Exception {
			var field = override.substring(1, override.indexOf('"', 1));
			mvc.perform(post("/api/v1/local-pokemon").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(withField(field, override)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").value("Invalid Pokémon data"))
					.andExpect(jsonPath("$.field").value(field));

			verifyNoInteractions(createLocalPokemon);
		}

		@Test
		void statsOutOfRangeAreBadRequest() throws Exception {
			mvc.perform(post("/api/v1/local-pokemon").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(NEW_POKEMON.replace("\"hp\": 33", "\"hp\": 256")))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.field").value("stats"))
					.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("hp")));
		}

		@Test
		void aNameTakenIsAConflict() throws Exception {
			given(createLocalPokemon.create(any())).willThrow(new PokemonNameTakenException("missingno"));

			mvc.perform(post("/api/v1/local-pokemon").with(admin()).contentType(MediaType.APPLICATION_JSON).content(NEW_POKEMON))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.title").value("Name taken"));
		}

		@Test
		void onlyAnAdminMayCreate() throws Exception {
			mvc.perform(post("/api/v1/local-pokemon").with(user()).contentType(MediaType.APPLICATION_JSON).content(NEW_POKEMON))
					.andExpect(status().isForbidden());
			mvc.perform(post("/api/v1/local-pokemon").contentType(MediaType.APPLICATION_JSON).content(NEW_POKEMON))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(createLocalPokemon);
		}
	}

	/** Replaces the value of one top-level field of {@link #NEW_POKEMON}. */
	private static String withField(String field, String replacement) {
		var json = new tools.jackson.databind.json.JsonMapper();
		var tree = (tools.jackson.databind.node.ObjectNode) json.readTree(NEW_POKEMON);
		var value = json.readTree("{" + replacement + "}").get(field);
		tree.set(field, value);
		return tree.toString();
	}

	@Nested
	class Update {

		@Test
		void aProprietaryEditCarriesNoCatalogPartAndTheVersion() throws Exception {
			given(updateLocalPokemon.update(any())).willReturn(PIKACHU);

			mvc.perform(put("/api/v1/local-pokemon/7").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(PROPRIETARY_EDIT))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.version").value(2));

			var command = ArgumentCaptor.forClass(UpdateLocalPokemonCommand.class);
			verify(updateLocalPokemon).update(command.capture());
			assertThat(command.getValue().id()).isEqualTo(7);
			assertThat(command.getValue().version()).isEqualTo(2);
			assertThat(command.getValue().entry()).isNull();
			assertThat(command.getValue().proprietary().tags()).containsExactly("mascot");
			assertThat(command.getValue().proprietary().localizedNames()).containsOnlyKeys("ja", "pt-BR");
		}

		@Test
		void aFullEditCarriesTheCatalogPart() throws Exception {
			given(updateLocalPokemon.update(any())).willReturn(PIKACHU);
			var body = withField("version", "\"version\": 0");

			mvc.perform(put("/api/v1/local-pokemon/8").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isOk());

			var command = ArgumentCaptor.forClass(UpdateLocalPokemonCommand.class);
			verify(updateLocalPokemon).update(command.capture());
			assertThat(command.getValue().entry().name()).isEqualTo("missingno");
			assertThat(command.getValue().entry().stats().attack()).isEqualTo(136);
		}

		@Test
		void aPartialCatalogPartIsBadRequestNamingWhatIsMissing() throws Exception {
			mvc.perform(put("/api/v1/local-pokemon/8").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content("{ \"version\": 0, \"name\": \"renamed\" }"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.field").value("heightM"));

			verifyNoInteractions(updateLocalPokemon);
		}

		@Test
		void theVersionIsRequired() throws Exception {
			mvc.perform(put("/api/v1/local-pokemon/7").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(PROPRIETARY_EDIT.replace("\"version\": 2,", "")))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.version").exists());

			verifyNoInteractions(updateLocalPokemon);
		}

		@Test
		void aNegativeVersionIsBadRequest() throws Exception {
			mvc.perform(put("/api/v1/local-pokemon/7").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(PROPRIETARY_EDIT.replace("\"version\": 2", "\"version\": -1")))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.field").value("version"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "id", "pokedexNumber", "syncedAt", "nickname" })
		void anUnknownFieldIsBadRequestNamingIt(String field) throws Exception {
			mvc.perform(put("/api/v1/local-pokemon/7").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(PROPRIETARY_EDIT.replaceFirst("\\{", "{ \"" + field + "\": 1,")))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").value("Unknown field"))
					.andExpect(jsonPath("$.field").value(field));

			verifyNoInteractions(updateLocalPokemon);
		}

		@Test
		void aWrongJsonTypeIsBadRequestNamingTheField() throws Exception {
			mvc.perform(post("/api/v1/local-pokemon").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(NEW_POKEMON.replace("\"hp\": 33", "\"hp\": \"high\"")))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").value("Invalid field value"))
					.andExpect(jsonPath("$.field").value("stats.hp"));
		}

		@Test
		void aFractionalStatIsNotSilentlyTruncated() throws Exception {
			mvc.perform(post("/api/v1/local-pokemon").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(NEW_POKEMON.replace("\"hp\": 33", "\"hp\": 33.7")))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.field").value("stats.hp"));

			verifyNoInteractions(createLocalPokemon);
		}

		@ParameterizedTest
		@ValueSource(strings = { "", "{", "not json", "[]" })
		void aMalformedBodyIsBadRequest(String body) throws Exception {
			mvc.perform(put("/api/v1/local-pokemon/7").with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

			verifyNoInteractions(updateLocalPokemon);
		}

		@Test
		void aBodyThatIsNotJsonIsUnsupported() throws Exception {
			mvc.perform(put("/api/v1/local-pokemon/7").with(admin()).contentType(MediaType.TEXT_PLAIN).content("hello"))
					.andExpect(status().isUnsupportedMediaType())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}

		@Test
		void editingACatalogFieldOfASyncedPokemonIsBadRequest() throws Exception {
			given(updateLocalPokemon.update(any())).willThrow(new CatalogFieldsNotEditableException(7));

			mvc.perform(put("/api/v1/local-pokemon/7").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(withField("version", "\"version\": 2")))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").value("Catalog fields not editable"));
		}

		@Test
		void aStaleVersionIsAConflict() throws Exception {
			given(updateLocalPokemon.update(any())).willThrow(new ConcurrentUpdateException("stale"));

			mvc.perform(put("/api/v1/local-pokemon/7").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(PROPRIETARY_EDIT))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.title").value("Concurrent update"));
		}

		@Test
		void aMissingPokemonIsNotFound() throws Exception {
			given(updateLocalPokemon.update(any())).willThrow(new LocalPokemonNotFoundException(404));

			mvc.perform(put("/api/v1/local-pokemon/404").with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(PROPRIETARY_EDIT))
					.andExpect(status().isNotFound());
		}

		@ParameterizedTest
		@ValueSource(strings = { "0", "abc", "-1" })
		void anInvalidIdIsBadRequest(String id) throws Exception {
			mvc.perform(put("/api/v1/local-pokemon/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
							.content(PROPRIETARY_EDIT))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").value("Invalid id"));

			verifyNoInteractions(updateLocalPokemon);
		}

		@Test
		void onlyAnAdminMayEdit() throws Exception {
			mvc.perform(put("/api/v1/local-pokemon/7").with(user()).contentType(MediaType.APPLICATION_JSON)
							.content(PROPRIETARY_EDIT))
					.andExpect(status().isForbidden());
			mvc.perform(put("/api/v1/local-pokemon/7").contentType(MediaType.APPLICATION_JSON).content(PROPRIETARY_EDIT))
					.andExpect(status().isUnauthorized());

			verifyNoInteractions(updateLocalPokemon);
		}
	}

	@Nested
	class Delete {

		@Test
		void answersNoContent() throws Exception {
			mvc.perform(delete("/api/v1/local-pokemon/7").with(admin()))
					.andExpect(status().isNoContent())
					.andExpect(content().string(""));

			verify(deleteLocalPokemon).delete(7);
		}

		@Test
		void aMissingPokemonIsNotFound() throws Exception {
			willThrow(new LocalPokemonNotFoundException(404)).given(deleteLocalPokemon).delete(anyLong());

			mvc.perform(delete("/api/v1/local-pokemon/404").with(admin()))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.title").value("Local Pokémon not found"));
		}

		@Test
		void anInvalidIdIsBadRequest() throws Exception {
			mvc.perform(delete("/api/v1/local-pokemon/0").with(admin())).andExpect(status().isBadRequest());

			verifyNoInteractions(deleteLocalPokemon);
		}

		@Test
		void onlyAnAdminMayDelete() throws Exception {
			mvc.perform(delete("/api/v1/local-pokemon/7").with(user())).andExpect(status().isForbidden());
			mvc.perform(delete("/api/v1/local-pokemon/7")).andExpect(status().isUnauthorized());

			verifyNoInteractions(deleteLocalPokemon);
		}
	}

	@Test
	void anUnknownFieldInsideANestedObjectIsNamedByItsPath() throws Exception {
		mvc.perform(put("/api/v1/local-pokemon/7").with(admin()).contentType(MediaType.APPLICATION_JSON)
						.content(PROPRIETARY_EDIT.replace("\"latitude\"", "\"altitude\": 1, \"latitude\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.field").value("coordinates.altitude"));
	}
}
