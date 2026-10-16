package com.app.poke.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.app.poke.adapter.out.security.BCryptPasswordHasher;
import com.app.poke.support.PostgresContainer;

/** The demo seed: users and Pokémon are pre-inserted only when the seed location is enabled; the documented passwords work. */
@Testcontainers
class DemoSeedTest {

	@Container
	static final PostgreSQLContainer POSTGRES = PostgresContainer.create();

	private static DriverManagerDataSource dataSource;
	private static JdbcTemplate db;
	private static int usersWithoutSeed;
	private static int pokemonWithoutSeed;

	@BeforeAll
	static void migrateWithoutThenWithTheSeed() {
		dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
		db = new JdbcTemplate(dataSource);
		migrate("classpath:db/migration");
		usersWithoutSeed = db.queryForObject("SELECT count(*) FROM app_user", Integer.class);
		pokemonWithoutSeed = db.queryForObject("SELECT count(*) FROM pokemon", Integer.class);
		migrate("classpath:db/migration", "classpath:db/seed");
	}

	private static void migrate(String... locations) {
		Flyway.configure().dataSource(dataSource).locations(locations).load().migrate();
	}

	@Test
	void withoutTheDemoProfileTheDatabaseHasNoUsers() {
		assertThat(usersWithoutSeed).isZero();
	}

	@ParameterizedTest(name = "{0} / {2} is {3}")
	@CsvSource({ "ash, ash@pallet.town, pikachu123, USER", "oak, oak@pallet.town, professor123, ADMIN" })
	void theDocumentedDemoCredentialsWork(String username, String email, String password, String role) {
		var row = db.queryForMap("SELECT email, password_hash, role FROM app_user WHERE username = ?", username);

		assertThat(row).containsEntry("email", email).containsEntry("role", role);
		assertThat(new BCryptPasswordHasher().matches(password, (String) row.get("password_hash"))).isTrue();
	}

	@Test
	void withoutTheDemoProfileThereAreNoPokemonEither() {
		assertThat(pokemonWithoutSeed).isZero();
	}

	@Test
	void demoPokemonArePreInsertedWithCatalogAndProprietaryData() {
		assertThat(db.queryForList("SELECT pokedex_number FROM pokemon ORDER BY pokedex_number", Integer.class))
				.containsExactly(1, 4, 7, 25, 133, 150);
		var pikachu = db.queryForMap("SELECT name, region, habitat, latitude, synced_at FROM pokemon WHERE pokedex_number = 25");
		assertThat(pikachu).containsEntry("name", "pikachu").containsEntry("region", "Kanto").containsEntry("habitat", "forest");
		assertThat(pikachu.get("latitude")).isNotNull();
		assertThat(pikachu.get("synced_at")).as("a synced copy, not a local-only one").isNotNull();
	}

	@Test
	void everyDemoPokemonHasItsChildRows() {
		for (var table : new String[] { "pokemon_type", "pokemon_ability", "pokemon_localized_name", "pokemon_tag" }) {
			assertThat(db.queryForObject("SELECT count(DISTINCT pokemon_id) FROM " + table, Integer.class)).as(table).isEqualTo(6);
		}
		assertThat(db.queryForList("SELECT tag FROM pokemon_tag t JOIN pokemon p ON p.id = t.pokemon_id "
				+ "WHERE p.pokedex_number = 150 ORDER BY tag", String.class)).containsExactly("gen-1", "legendary");
	}

	@Test
	void reApplyingTheSeedChangesNothing() {
		db.update("DELETE FROM flyway_schema_history WHERE script LIKE 'R__%'"); // force the repeatable to run again
		migrate("classpath:db/migration", "classpath:db/seed");

		assertThat(db.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isEqualTo(2);
		assertThat(db.queryForObject("SELECT count(*) FROM pokemon", Integer.class)).isEqualTo(6);
		assertThat(db.queryForObject("SELECT count(*) FROM pokemon_type", Integer.class)).isEqualTo(7);
	}
}
