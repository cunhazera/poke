package com.app.poke.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.app.poke.application.exception.ConcurrentUpdateException;
import com.app.poke.domain.exception.PokemonNameTakenException;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.BaseStats;
import com.app.poke.domain.model.Coordinates;
import com.app.poke.domain.model.Height;
import com.app.poke.domain.model.LocalPokemon;
import com.app.poke.domain.model.PageRequest;
import com.app.poke.domain.model.PokedexEntry;
import com.app.poke.domain.model.PokedexNumber;
import com.app.poke.domain.model.ProprietaryData;
import com.app.poke.domain.model.Weight;
import com.app.poke.support.TestcontainersConfiguration;

import jakarta.persistence.EntityManagerFactory;

/** The real gateway, Hibernate mapping and Flyway schema on PostgreSQL; each call commits like in production. */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JpaLocalPokemonGateway.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaLocalPokemonGatewayTest {

	private static final Instant SYNCED = Instant.parse("2026-10-15T12:34:56.123456Z");
	private static final BaseStats STATS = new BaseStats(45, 49, 49, 65, 65, 45);
	private static final ProprietaryData ENRICHED = new ProprietaryData(
			Map.of("pt-BR", "Bulbassauro", "ja", "フシギダネ"), "Kanto", "grassland",
			new Coordinates(new BigDecimal("35.676200"), new BigDecimal("-139.650300")), Set.of("starter", "gen-1"));

	@Autowired
	private JpaLocalPokemonGateway gateway;

	@Autowired
	private JdbcTemplate db;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	void empty() {
		db.execute("TRUNCATE pokemon RESTART IDENTITY CASCADE");
	}

	/** Types and abilities deliberately not in alphabetical order, to prove slot order is kept. */
	private static PokedexEntry entry(String name, String description, List<String> types) {
		return new PokedexEntry(name, "Seed Pokémon", description, Height.ofDecimetres(7), Weight.ofHectograms(69),
				"https://sprites/1.png", "https://art/1.png", types,
				List.of(new Ability("overgrow", false), new Ability("chlorophyll", true)), STATS);
	}

	private LocalPokemon synced(int number, String name) {
		return gateway.save(LocalPokemon.fromCatalog(new PokedexNumber(number), entry(name, "text", List.of("normal")), SYNCED));
	}

	private LocalPokemon enrichedBulbasaur() {
		var first = gateway.save(LocalPokemon.fromCatalog(new PokedexNumber(1),
				entry("bulbasaur", "A strange seed.", List.of("poison", "grass")), SYNCED));
		return gateway.save(new LocalPokemon(first.id(), 1, first.entry(), ENRICHED, first.syncedAt(), first.version()));
	}

	@Test
	void roundTripsEveryFieldKeepingSlotOrder() {
		var stored = enrichedBulbasaur();

		var read = gateway.findById(stored.id()).orElseThrow();

		assertThat(read).isEqualTo(stored);
		assertThat(read.entry().types()).containsExactly("poison", "grass");
		assertThat(read.entry().abilities()).extracting(Ability::name).containsExactly("overgrow", "chlorophyll");
		assertThat(read.proprietary()).isEqualTo(ENRICHED);
		assertThat(read.syncedAt()).isEqualTo(SYNCED);
	}

	@Test
	void slotsAreStoredFromOne() {
		var stored = enrichedBulbasaur();

		assertThat(db.queryForList("SELECT slot FROM pokemon_type WHERE pokemon_id = ? ORDER BY slot", Integer.class, stored.id()))
				.containsExactly(1, 2);
	}

	@Test
	void everySaveBumpsTheVersion() {
		var first = synced(25, "pikachu");
		var second = gateway.save(first.refreshedFrom(first.entry(), SYNCED.plusSeconds(60)));

		assertThat(first.version()).isZero();
		assertThat(second.version()).isEqualTo(1);
	}

	@Test
	void aReSyncReplacesTypesAndAbilitiesAndKeepsTheProprietaryRows() {
		var stored = enrichedBulbasaur();
		var fewer = new PokedexEntry("bulbasaur", "Seed Pokémon", "New text.", Height.ofDecimetres(7),
				Weight.ofHectograms(69), null, null, List.of("grass"), List.of(new Ability("overgrow", false)), STATS);

		gateway.save(stored.refreshedFrom(fewer, SYNCED.plusSeconds(60)));

		var read = gateway.findById(stored.id()).orElseThrow();
		assertThat(read.entry().types()).containsExactly("grass");
		assertThat(read.entry().abilities()).hasSize(1);
		assertThat(read.entry().description()).isEqualTo("New text.");
		assertThat(read.proprietary()).isEqualTo(ENRICHED);
		assertThat(db.queryForObject("SELECT count(*) FROM pokemon_type", Integer.class)).isEqualTo(1);
	}

	@Test
	void aStaleCopyIsRejectedAndChangesNothing() {
		var stored = enrichedBulbasaur();
		gateway.save(stored.refreshedFrom(entry("bulbasaur", "Someone else's sync.", List.of("grass")), SYNCED));

		assertThatThrownBy(() -> gateway.save(stored.refreshedFrom(entry("bulbasaur", "Our stale sync.", List.of("grass")), SYNCED)))
				.isInstanceOf(ConcurrentUpdateException.class);
		assertThat(gateway.findById(stored.id()).orElseThrow().entry().description()).isEqualTo("Someone else's sync.");
	}

	@Test
	void aSecondFirstSyncOfTheSameNumberIsAConflict() {
		synced(25, "pikachu");

		assertThatThrownBy(() -> synced(25, "pikachu-again")).isInstanceOf(ConcurrentUpdateException.class);
		assertThat(db.queryForObject("SELECT count(*) FROM pokemon", Integer.class)).isEqualTo(1);
	}

	@Test
	void findsByPokedexNumber() {
		var pikachu = synced(25, "pikachu");

		assertThat(gateway.findByPokedexNumber(25)).contains(pikachu);
		assertThat(gateway.findByPokedexNumber(26)).isEmpty();
		assertThat(gateway.findById(999)).isEmpty();
	}

	@Test
	void listsInPokedexOrderWithLocalOnlyPokemonLast() {
		synced(25, "pikachu");
		gateway.save(new LocalPokemon(null, null, entry("homebrewmon", null, List.of("normal")), ProprietaryData.EMPTY, null, 0));
		synced(1, "bulbasaur");

		var page = gateway.findAll(new PageRequest(0, 10));

		assertThat(page.items()).extracting(p -> p.entry().name()).containsExactly("bulbasaur", "pikachu", "homebrewmon");
		assertThat(page.totalItems()).isEqualTo(3);
	}

	@Test
	void aPageLoadsItsCollectionsInBatchesNotOneQueryPerRow() {
		for (int n = 1; n <= 10; n++) {
			synced(n, "mon" + n);
		}
		var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		var page = gateway.findAll(new PageRequest(0, 10));

		assertThat(page.items()).hasSize(10).allSatisfy(p -> assertThat(p.entry().types()).isNotEmpty());
		// page + count + one batched query per collection (types, abilities, names, tags); N+1 would be 2 + 40
		assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);
	}

	@Test
	void deletingRemovesTheRowAndAllItsChildRows() {
		var bulbasaur = enrichedBulbasaur();
		var pikachu = synced(25, "pikachu");

		assertThat(gateway.deleteById(bulbasaur.id())).isTrue();

		assertThat(gateway.findById(bulbasaur.id())).isEmpty();
		assertThat(gateway.findById(pikachu.id())).isPresent();
		for (var child : List.of("pokemon_type", "pokemon_ability", "pokemon_localized_name", "pokemon_tag")) {
			assertThat(db.queryForObject("SELECT count(*) FROM " + child + " WHERE pokemon_id = ?", Long.class, bulbasaur.id()))
					.as(child).isZero();
		}
	}

	@Test
	void deletingAMissingPokemonReportsIt() {
		assertThat(gateway.deleteById(404)).isFalse();
	}

	@Test
	void aDeletedPokemonCanBeStoredAgainUnderItsNumber() {
		var first = synced(25, "pikachu");
		gateway.deleteById(first.id());

		var again = synced(25, "pikachu");

		assertThat(again.id()).isNotEqualTo(first.id());
		assertThat(gateway.findByPokedexNumber(25)).contains(again);
	}

	@Test
	void aLocalPokemonTakingASyncedNameInAnyCaseIsRejected() {
		synced(25, "pikachu");

		assertThatThrownBy(() -> gateway.save(LocalPokemon.createdLocally(entry("pikachu", "a copycat", List.of("normal")),
				ProprietaryData.EMPTY))).isInstanceOf(PokemonNameTakenException.class).hasMessageContaining("pikachu");
		assertThat(db.queryForObject("SELECT count(*) FROM pokemon", Long.class)).isEqualTo(1);
	}

	@Test
	void aSyncOntoTheNameOfALocalPokemonIsRejected() {
		gateway.save(LocalPokemon.createdLocally(entry("mew", "made here first", List.of("psychic")), ProprietaryData.EMPTY));

		assertThatThrownBy(() -> synced(151, "mew")).isInstanceOf(PokemonNameTakenException.class);
	}

	@Test
	void renamingALocalPokemonUpdatesItInPlace() {
		var created = gateway.save(LocalPokemon.createdLocally(entry("homebrewmon", "draft", List.of("normal")),
				ProprietaryData.EMPTY));

		var renamed = gateway.save(created.edited(entry("homebrewmon-x", "final", List.of("fire", "flying")), ENRICHED,
				created.version()));

		assertThat(gateway.findById(created.id())).contains(renamed);
		assertThat(renamed.entry().types()).containsExactly("fire", "flying");
		assertThat(renamed.pokedexNumber()).isNull();
		assertThat(renamed.syncedAt()).isNull();
		assertThat(renamed.version()).isEqualTo(created.version() + 1);
	}
}
