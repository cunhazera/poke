package com.app.poke.application.usecase;

import static com.app.poke.application.usecase.CatalogFixtures.STATS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.exception.ConcurrentUpdateException;
import com.app.poke.application.port.in.SyncPokemonCommand;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.application.port.out.SpeciesProfile;
import com.app.poke.domain.exception.PokemonNotFoundException;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.Coordinates;
import com.app.poke.domain.model.Height;
import com.app.poke.domain.model.LocalPokemon;
import com.app.poke.domain.model.PokedexEntry;
import com.app.poke.domain.model.ProprietaryData;
import com.app.poke.domain.model.Weight;

class SyncPokemonInteractorTest {

	private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
	private static final ProprietaryData ENRICHED = new ProprietaryData(Map.of("pt-BR", "Bulbassauro"), "Kanto",
			"grassland", new Coordinates(new BigDecimal("35.6762"), new BigDecimal("139.6503")), Set.of("starter"));
	private static final PokemonProfile BULBASAUR = new PokemonProfile(1, "bulbasaur", "https://sprites/1.png",
			"https://art/1.png", Height.ofDecimetres(7), Weight.ofHectograms(69), List.of("grass", "poison"),
			List.of(new Ability("overgrow", false), new Ability("chlorophyll", true)), STATS);

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final FakePokemonCatalog catalog = new FakePokemonCatalog();
	private final InMemoryLocalPokemonStore store = new InMemoryLocalPokemonStore();
	private final SyncPokemonInteractor interactor = new SyncPokemonInteractor(catalog, store, executor,
			Clock.fixed(NOW, ZoneOffset.UTC));

	@AfterEach
	void shutdown() {
		executor.close();
	}

	private void catalogHasBulbasaur(String description) {
		catalog.add(BULBASAUR, new SpeciesProfile("Seed Pokémon", description, 1));
	}

	@Test
	void aFirstSyncCopiesTheCatalogEntry() {
		catalogHasBulbasaur("A strange seed was planted on its back at birth.");

		var result = interactor.sync(SyncPokemonCommand.of(1));

		assertThat(result.created()).isTrue();
		assertThat(result.pokemon().id()).isNotNull();
		assertThat(result.pokemon().pokedexNumber()).isEqualTo(1);
		assertThat(result.pokemon().syncedAt()).isEqualTo(NOW);
		assertThat(result.pokemon().proprietary()).isEqualTo(ProprietaryData.EMPTY);
		assertThat(result.pokemon().entry()).isEqualTo(new PokedexEntry("bulbasaur", "Seed Pokémon",
				"A strange seed was planted on its back at birth.", Height.ofDecimetres(7), Weight.ofHectograms(69),
				"https://sprites/1.png", "https://art/1.png", List.of("grass", "poison"),
				List.of(new Ability("overgrow", false), new Ability("chlorophyll", true)), STATS));
	}

	@Test
	void aReSyncRefreshesTheCatalogPartAndKeepsOurData() {
		catalogHasBulbasaur("The new text.");
		var stored = store.insert(new LocalPokemon(null, 1, LocalPokemonFixtures.entry("bulbasaur", "The old text."),
				ENRICHED, Instant.parse("2026-01-01T00:00:00Z"), 0));

		var result = interactor.sync(SyncPokemonCommand.of(1));

		assertThat(result.created()).isFalse();
		assertThat(result.pokemon().id()).isEqualTo(stored.id());
		assertThat(result.pokemon().entry().description()).isEqualTo("The new text.");
		assertThat(result.pokemon().proprietary()).isEqualTo(ENRICHED);
		assertThat(result.pokemon().syncedAt()).isEqualTo(NOW);
		assertThat(store.rows).hasSize(1);
	}

	@Test
	void anUnknownSpeciesIsNotFoundAndNothingIsStored() {
		catalog.add(CatalogFixtures.profile(10001, "deoxys-attack"), "x").withoutSpecies(10001);

		assertThatThrownBy(() -> interactor.sync(SyncPokemonCommand.of(10001))).isInstanceOf(PokemonNotFoundException.class);
		assertThat(store.saves).isZero();
	}

	@Test
	void anUnknownPokemonIsNotFound() {
		assertThatThrownBy(() -> interactor.sync(SyncPokemonCommand.of(99999))).isInstanceOf(PokemonNotFoundException.class);
	}

	@Test
	void anUpstreamOutageChangesNothing() {
		catalogHasBulbasaur("x");
		var stored = store.insert(new LocalPokemon(null, 1, LocalPokemonFixtures.entry("bulbasaur", "kept"), ENRICHED,
				Instant.parse("2026-01-01T00:00:00Z"), 0));
		var outage = new CatalogUnavailableException("PokeAPI down", null);
		catalog.failOn(1, outage);

		assertThatThrownBy(() -> interactor.sync(SyncPokemonCommand.of(1))).isSameAs(outage);
		assertThat(store.rows).containsExactly(stored);
	}

	@Test
	void catalogDataThatBreaksADomainRuleIsAnUpstreamFailure() {
		catalog.add(new PokemonProfile(1, "bulbasaur", null, null, Height.ofDecimetres(7), Weight.ofHectograms(69),
				List.of(), List.of(), STATS), new SpeciesProfile(null, null, null));

		assertThatThrownBy(() -> interactor.sync(SyncPokemonCommand.of(1)))
				.isInstanceOf(CatalogUnavailableException.class)
				.hasCauseInstanceOf(IllegalArgumentException.class);
		assertThat(store.saves).isZero();
	}

	@Test
	void aConcurrentChangeIsReportedNotOverwritten() {
		catalogHasBulbasaur("x");
		store.insert(new LocalPokemon(null, 1, LocalPokemonFixtures.entry("bulbasaur", "old"), ENRICHED, NOW, 0));
		store.beforeSave(() -> {
			var current = store.rows.getFirst();
			store.rows.set(0, new LocalPokemon(current.id(), 1, current.entry(), ProprietaryData.EMPTY, NOW, 1));
		});

		assertThatThrownBy(() -> interactor.sync(SyncPokemonCommand.of(1))).isInstanceOf(ConcurrentUpdateException.class);
	}

	@Test
	void fetchesThePokemonAndItsSpeciesConcurrently() {
		catalogHasBulbasaur("x");
		var bothInFlight = new CyclicBarrier(2);
		catalog.onFetch(id -> {
			try {
				bothInFlight.await(2, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new AssertionError("pokemon(1) and species(1) were not fetched concurrently", e);
			}
		});

		assertThat(interactor.sync(SyncPokemonCommand.of(1)).created()).isTrue();
	}
}
