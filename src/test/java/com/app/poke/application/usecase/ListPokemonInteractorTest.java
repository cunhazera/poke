package com.app.poke.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.in.ListPokemonQuery;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.domain.exception.PokemonNotFoundException;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.PokemonSummary;
import com.app.poke.domain.model.Weight;

class ListPokemonInteractorTest {

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final FakePokemonCatalog catalog = new FakePokemonCatalog();
	private final ListPokemonInteractor interactor = new ListPokemonInteractor(catalog, executor);

	@AfterEach
	void shutdown() {
		executor.close();
	}

	private static PokemonProfile profile(int id, String name) {
		return new PokemonProfile(id, name, "https://sprites/" + id + ".png", Weight.ofHectograms(id * 10),
				List.of(new Ability(name + "-ability", false)));
	}

	private void seed(int count) {
		IntStream.rangeClosed(1, count).forEach(id -> catalog.add(profile(id, "mon" + id), "Category " + id));
	}

	@Test
	void combinesProfileAndCategoryIntoSummaries() {
		catalog.add(new PokemonProfile(1, "bulbasaur", "https://sprites/1.png", Weight.ofHectograms(69),
				List.of(new Ability("overgrow", false), new Ability("chlorophyll", true))), "Seed Pokémon");

		var page = interactor.list(ListPokemonQuery.of(0, 20));

		assertThat(page.items()).containsExactly(new PokemonSummary(1, "bulbasaur", "https://sprites/1.png",
				"Seed Pokémon", Weight.ofHectograms(69),
				List.of(new Ability("overgrow", false), new Ability("chlorophyll", true))));
		assertThat(page.totalItems()).isEqualTo(1);
	}

	@Test
	void translatesPageNumberIntoOffsetAndReportsUpstreamTotal() {
		seed(25);

		var page = interactor.list(ListPokemonQuery.of(2, 10));

		assertThat(catalog.calls).contains("list(20,10)");
		assertThat(page.items()).extracting(PokemonSummary::id).containsExactly(21, 22, 23, 24, 25);
		assertThat(page.totalItems()).isEqualTo(25);
		assertThat(page.totalPages()).isEqualTo(3);
	}

	@Test
	void keepsUpstreamOrderEvenWhenLaterEntriesResolveFirst() throws Exception {
		seed(3);
		var lastEntryFetched = new CountDownLatch(2); // pokemon(3) and category(3)
		catalog.onFetch(id -> {
			if (id == 3) {
				lastEntryFetched.countDown();
			} else {
				await(lastEntryFetched);
			}
		});

		var page = interactor.list(ListPokemonQuery.of(0, 3));

		assertThat(page.items()).extracting(PokemonSummary::id).containsExactly(1, 2, 3);
	}

	@Test
	void fetchesAllEntriesConcurrently() {
		seed(3);
		// 3 entries x (pokemon + category) = 6 calls; the barrier only trips if all are in flight at once.
		var allInFlight = new CyclicBarrier(6);
		catalog.onFetch(id -> {
			try {
				allInFlight.await(2, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new AssertionError("fetches were not concurrent", e);
			}
		});

		assertThat(interactor.list(ListPokemonQuery.of(0, 3)).items()).hasSize(3);
	}

	@Test
	void pageBeyondTheEndIsEmptyButKeepsTotals() {
		seed(5);

		var page = interactor.list(ListPokemonQuery.of(9, 5));

		assertThat(page.items()).isEmpty();
		assertThat(page.totalItems()).isEqualTo(5);
		assertThat(catalog.calls).containsExactly("list(45,5)");
	}

	@Test
	void missingCategoryBecomesNull() {
		catalog.add(profile(1, "missingno"), null);

		var page = interactor.list(ListPokemonQuery.of(0, 1));

		assertThat(page.items().getFirst().category()).isNull();
	}

	@Test
	void upstreamFailureOnAnyEntryFailsTheWholePageWithTheOriginalException() {
		seed(3);
		var outage = new CatalogUnavailableException("PokeAPI down", null);
		catalog.failOn(2, outage);

		assertThatThrownBy(() -> interactor.list(ListPokemonQuery.of(0, 3))).isSameAs(outage);
	}

	@Test
	void entryListedButNotFoundIsReportedAsCatalogInconsistency() {
		seed(2);
		catalog.listedButMissing(2);

		assertThatThrownBy(() -> interactor.list(ListPokemonQuery.of(0, 2)))
				.isInstanceOf(CatalogUnavailableException.class)
				.hasMessageContaining("2")
				.hasCauseInstanceOf(PokemonNotFoundException.class);
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(2, TimeUnit.SECONDS)) {
				throw new AssertionError("timed out waiting for entry 3");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
	}
}
