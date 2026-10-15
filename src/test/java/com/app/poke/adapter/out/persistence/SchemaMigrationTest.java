package com.app.poke.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.app.poke.support.PostgresContainer;

/**
 * Runs the real Flyway migrations on a real PostgreSQL and proves each constraint with an actual INSERT.
 * The domain validates first; these constraints are the last line of defense, so each one must really fire.
 */
@Testcontainers
class SchemaMigrationTest {

	@Container
	static final PostgreSQLContainer POSTGRES = PostgresContainer.create();

	private static Flyway flyway;
	private static JdbcTemplate db;

	@BeforeAll
	static void migrate() {
		var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		flyway = Flyway.configure().dataSource(dataSource).load();
		flyway.migrate();
		db = new JdbcTemplate(dataSource);
	}

	@BeforeEach
	void empty() {
		db.execute("TRUNCATE pokemon, app_user RESTART IDENTITY CASCADE");
	}

	/** Inserts a valid Pokémon (Bulbasaur's numbers) and returns its surrogate id. */
	private static long insertPokemon(Integer pokedexNumber, String name) {
		return insertPokemon(pokedexNumber, name, Map.of());
	}

	/** As above, plus {@code extra} columns, e.g. the proprietary fields. */
	private static long insertPokemon(Integer pokedexNumber, String name, Map<String, ?> extra) {
		var columns = new ArrayList<>(List.of("pokedex_number", "name", "height_dm", "weight_hg",
				"hp", "attack", "defense", "special_attack", "special_defense", "speed"));
		var values = new ArrayList<Object>(Arrays.asList(pokedexNumber, name, 7, 69, 45, 49, 49, 65, 65, 45));
		extra.forEach((column, value) -> {
			columns.add(column);
			values.add(value);
		});
		var sql = "INSERT INTO pokemon (%s) VALUES (%s) RETURNING id"
				.formatted(String.join(", ", columns), String.join(", ", Collections.nCopies(columns.size(), "?")));
		return db.queryForObject(sql, Long.class, values.toArray());
	}

	private static void insertUser(String username, String email) {
		db.update("INSERT INTO app_user (username, email, password_hash) VALUES (?, ?, 'bcrypt-hash')", username, email);
	}

	/** Asserts the statement is rejected by exactly the named constraint, not by some other rule. */
	private static void assertViolates(String constraint, Runnable statement) {
		assertThatThrownBy(statement::run)
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("\"" + constraint + "\"");
	}

	@Test
	void migrationsApplyFromAnEmptyDatabaseAndValidate() {
		assertThat(flyway.info().applied()).extracting(m -> m.getVersion().getVersion()).containsExactly("1", "2");
		assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
	}

	@Nested
	class Pokemon {

		@Test
		void acceptsAFullPokemonWithAllProprietaryData() {
			var id = insertPokemon(1, "Bulbasaur", Map.of("category", "Seed Pokémon", "description", "A strange seed…",
					"region", "Kanto", "habitat", "grassland",
					"latitude", new BigDecimal("-22.906847"), "longitude", new BigDecimal("-43.172897")));
			db.update("INSERT INTO pokemon_type VALUES (?, 1, 'grass'), (?, 2, 'poison')", id, id);
			db.update("INSERT INTO pokemon_ability VALUES (?, 1, 'overgrow', false), (?, 3, 'chlorophyll', true)", id, id);
			db.update("INSERT INTO pokemon_localized_name VALUES (?, 'pt-BR', 'Bulbassauro'), (?, 'ja', 'フシギダネ')", id, id);
			db.update("INSERT INTO pokemon_tag VALUES (?, 'starter'), (?, 'gen-1')", id, id);

			var row = db.queryForMap("SELECT version, created_at, synced_at FROM pokemon WHERE id = ?", id);
			assertThat(row.get("version")).isEqualTo(0L);
			assertThat(row.get("created_at")).isNotNull();
			assertThat(row.get("synced_at")).as("local until a sync stamps it").isNull();
		}

		@Test
		void pokedexNumberIsUnique() {
			insertPokemon(1, "Bulbasaur");

			assertViolates("pokemon_pokedex_number_key", () -> insertPokemon(1, "Bulbasaur-copy"));
		}

		@Test
		void locallyCreatedPokemonHaveNoPokedexNumberAndDoNotCollide() {
			insertPokemon(null, "Homebrewmon");

			assertThatCode(() -> insertPokemon(null, "Otherbrewmon")).doesNotThrowAnyException();
		}

		@Test
		void nameIsUniqueRegardlessOfCase() {
			insertPokemon(1, "Bulbasaur");

			assertViolates("uq_pokemon_name", () -> insertPokemon(2, "bulbasaur"));
		}

		@ParameterizedTest(name = "{0} = 0 and 256 are rejected")
		@ValueSource(strings = { "hp", "attack", "defense", "special_attack", "special_defense", "speed" })
		void everyStatIsWithin1And255(String stat) {
			var pokemon = insertPokemon(1, "Bulbasaur");

			assertViolates("pokemon_" + stat + "_check", () -> db.update("UPDATE pokemon SET " + stat + " = 0 WHERE id = ?", pokemon));
			assertViolates("pokemon_" + stat + "_check", () -> db.update("UPDATE pokemon SET " + stat + " = 256 WHERE id = ?", pokemon));
			assertThatCode(() -> db.update("UPDATE pokemon SET " + stat + " = 255 WHERE id = ?", pokemon)).doesNotThrowAnyException();
		}

		@Test
		void heightAndWeightCannotBeNegative() {
			var pokemon = insertPokemon(1, "Bulbasaur");

			assertViolates("pokemon_height_dm_check", () -> db.update("UPDATE pokemon SET height_dm = -1 WHERE id = ?", pokemon));
			assertViolates("pokemon_weight_hg_check", () -> db.update("UPDATE pokemon SET weight_hg = -1 WHERE id = ?", pokemon));
		}

		@Test
		void aCoordinateNeedsBothHalvesWithinRange() {
			assertViolates("ck_pokemon_coordinates", () -> insertPokemon(1, "Bulbasaur", Map.of("latitude", 10)));
			assertViolates("ck_pokemon_coordinates", () -> insertPokemon(1, "Bulbasaur", Map.of("longitude", 10)));
			assertViolates("pokemon_latitude_check",
					() -> insertPokemon(1, "Bulbasaur", Map.of("latitude", new BigDecimal("90.5"), "longitude", 0)));
			assertViolates("pokemon_longitude_check",
					() -> insertPokemon(1, "Bulbasaur", Map.of("latitude", 0, "longitude", new BigDecimal("-180.5"))));
		}

		@Test
		void atMostTwoTypesAndThreeAbilitySlots() {
			var id = insertPokemon(1, "Bulbasaur");

			assertViolates("pokemon_type_slot_check", () -> db.update("INSERT INTO pokemon_type VALUES (?, 3, 'bug')", id));
			assertViolates("pokemon_ability_slot_check", () -> db.update("INSERT INTO pokemon_ability VALUES (?, 4, 'x', false)", id));
		}

		@Test
		void aTypeSlotHoldsOneType() {
			var id = insertPokemon(1, "Bulbasaur");
			db.update("INSERT INTO pokemon_type VALUES (?, 1, 'grass')", id);

			assertViolates("pokemon_type_pkey", () -> db.update("INSERT INTO pokemon_type VALUES (?, 1, 'fire')", id));
		}

		@ParameterizedTest
		@ValueSource(strings = { "Starter", "has space", "-leading-dash", "" })
		void tagsAreLowercaseSlugs(String tag) {
			var id = insertPokemon(1, "Bulbasaur");

			assertViolates("pokemon_tag_tag_check", () -> db.update("INSERT INTO pokemon_tag VALUES (?, ?)", id, tag));
		}

		@ParameterizedTest
		@ValueSource(strings = { "Portuguese", "PT", "pt_BR", "p" })
		void localizedNamesUseLanguageTags(String language) {
			var id = insertPokemon(1, "Bulbasaur");

			assertViolates("pokemon_localized_name_language_check",
					() -> db.update("INSERT INTO pokemon_localized_name VALUES (?, ?, 'x')", id, language));
		}

		@Test
		void oneLocalizedNamePerLanguage() {
			var id = insertPokemon(1, "Bulbasaur");
			db.update("INSERT INTO pokemon_localized_name VALUES (?, 'pt-BR', 'Bulbassauro')", id);

			assertViolates("pokemon_localized_name_pkey",
					() -> db.update("INSERT INTO pokemon_localized_name VALUES (?, 'pt-BR', 'Outro')", id));
		}

		@Test
		void childRowsMustBelongToAnExistingPokemon() {
			assertViolates("pokemon_tag_pokemon_id_fkey", () -> db.update("INSERT INTO pokemon_tag VALUES (999, 'starter')"));
		}

		@Test
		void deletingAPokemonDeletesAllItsChildRows() {
			var id = insertPokemon(1, "Bulbasaur");
			db.update("INSERT INTO pokemon_type VALUES (?, 1, 'grass')", id);
			db.update("INSERT INTO pokemon_ability VALUES (?, 1, 'overgrow', false)", id);
			db.update("INSERT INTO pokemon_localized_name VALUES (?, 'pt-BR', 'Bulbassauro')", id);
			db.update("INSERT INTO pokemon_tag VALUES (?, 'starter')", id);

			db.update("DELETE FROM pokemon WHERE id = ?", id);

			for (var child : new String[] { "pokemon_type", "pokemon_ability", "pokemon_localized_name", "pokemon_tag" }) {
				assertThat(db.queryForObject("SELECT count(*) FROM " + child, Integer.class)).as(child).isZero();
			}
		}
	}

	@Nested
	class AppUser {

		@Test
		void storesTheCasingTheUserTypedAndDefaultsToTheUserRole() {
			insertUser("AshKetchum", "Ash@Pallet.town");

			var row = db.queryForMap("SELECT username, email, role FROM app_user");
			assertThat(row).containsEntry("username", "AshKetchum").containsEntry("email", "Ash@Pallet.town")
					.containsEntry("role", "USER");
		}

		@Test
		void usernameIsUniqueRegardlessOfCase() {
			insertUser("AshKetchum", "ash@pallet.town");

			assertViolates("uq_app_user_username", () -> insertUser("ashketchum", "other@pallet.town"));
		}

		@Test
		void emailIsUniqueRegardlessOfCase() {
			insertUser("AshKetchum", "Ash@Pallet.town");

			assertViolates("uq_app_user_email", () -> insertUser("misty", "ash@PALLET.TOWN"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "ab", "has space", "ash!", "" })
		void usernameIsAtLeastThreeSafeCharacters(String username) {
			assertViolates("app_user_username_check", () -> insertUser(username, "ash@pallet.town"));
		}

		@Test
		void usernameIsAtMostFiftyCharacters() {
			assertThatCode(() -> insertUser("a".repeat(50), "ash@pallet.town")).doesNotThrowAnyException();
			assertThatThrownBy(() -> insertUser("b".repeat(51), "brock@pewter.city")).hasMessageContaining("value too long");
		}

		@ParameterizedTest
		@ValueSource(strings = { "not-an-email", "ash@pallet", "@pallet.town", "ash @pallet.town" })
		void malformedEmailIsRejected(String email) {
			assertViolates("app_user_email_check", () -> insertUser("AshKetchum", email));
		}

		@Test
		void emailIsAtMost254Characters() {
			var tooLong = "a".repeat(245) + "@pallet.town"; // 257 characters

			assertThatThrownBy(() -> insertUser("AshKetchum", tooLong)).hasMessageContaining("value too long");
		}

		@Test
		void roleIsUserOrAdmin() {
			assertViolates("app_user_role_check", () -> db.update(
					"INSERT INTO app_user (username, email, password_hash, role) VALUES ('ash', 'a@b.io', 'h', 'ROOT')"));
		}
	}
}
