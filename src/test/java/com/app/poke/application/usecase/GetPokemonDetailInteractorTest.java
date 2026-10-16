package com.app.poke.application.usecase;

import static com.app.poke.application.usecase.CatalogFixtures.STATS;
import static com.app.poke.application.usecase.CatalogFixtures.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.in.GetPokemonDetailQuery;
import com.app.poke.application.port.out.EvolutionLink;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.application.port.out.SpeciesProfile;
import com.app.poke.domain.exception.PokemonNotFoundException;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.EvolutionStage;
import com.app.poke.domain.model.Height;
import com.app.poke.domain.model.PokemonDetail;
import com.app.poke.domain.model.Weight;

class GetPokemonDetailInteractorTest {

	private static final EvolutionLink BULBASAUR_LINE = new EvolutionLink(1, "bulbasaur",
			List.of(new EvolutionLink(2, "ivysaur", List.of(EvolutionLink.single(3, "venusaur")))));

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final FakePokemonCatalog catalog = new FakePokemonCatalog();
	private final GetPokemonDetailInteractor interactor = new GetPokemonDetailInteractor(catalog, executor);

	@AfterEach
	void shutdown() {
		executor.close();
	}

	private static SpeciesProfile species(Integer chainId) {
		return new SpeciesProfile("Seed Pokémon", "A bulb on its back.", chainId);
	}

	private void seedBulbasaurLine() {
		catalog.add(profile(1, "bulbasaur"), species(1))
				.add(profile(2, "ivysaur"), species(1))
				.add(profile(3, "venusaur"), species(1))
				.chain(1, BULBASAUR_LINE);
	}

	private PokemonDetail get(int id) {
		return interactor.get(GetPokemonDetailQuery.of(id));
	}

	@Test
	void combinesProfileSpeciesAndTheWholeChainWithASpritePerStage() {
		var ivysaur = new PokemonProfile(2, "ivysaur", "https://sprites/2.png", "https://art/2.png", Height.ofDecimetres(10),
				Weight.ofHectograms(130), List.of("grass", "poison"), List.of(new Ability("overgrow", false)), STATS);
		catalog.add(profile(1, "bulbasaur"), species(1)).add(ivysaur, species(1)).add(profile(3, "venusaur"), species(1))
				.chain(1, BULBASAUR_LINE);

		assertThat(get(2)).isEqualTo(new PokemonDetail(2, "ivysaur", "Seed Pokémon", "https://art/2.png",
				"https://sprites/2.png", "A bulb on its back.", Height.ofDecimetres(10), Weight.ofHectograms(130),
				List.of("grass", "poison"), List.of(new Ability("overgrow", false)), STATS,
				new EvolutionStage(1, "bulbasaur", "https://sprites/1.png", List.of(
						new EvolutionStage(2, "ivysaur", "https://sprites/2.png", List.of(
								new EvolutionStage(3, "venusaur", "https://sprites/3.png", List.of())))))));
	}

	@Test
	void fetchesTheChainBySpeciesChainIdNotByPokedexNumber() {
		catalog.add(profile(133, "eevee"), species(67)).add(profile(134, "vaporeon"), species(67))
				.chain(67, new EvolutionLink(133, "eevee", List.of(EvolutionLink.single(134, "vaporeon"))));

		get(133);

		assertThat(catalog.calls).contains("chain(67)").doesNotContain("chain(133)");
	}

	@Test
	void branchingChainKeepsEveryBranchInCatalogOrder() {
		catalog.add(profile(133, "eevee"), species(67)).add(profile(134, "vaporeon"), species(67))
				.add(profile(135, "jolteon"), species(67)).add(profile(136, "flareon"), species(67))
				.chain(67, new EvolutionLink(133, "eevee", List.of(EvolutionLink.single(134, "vaporeon"),
						EvolutionLink.single(135, "jolteon"), EvolutionLink.single(136, "flareon"))));

		var chain = get(135).evolutionChain();

		assertThat(chain.id()).as("the chain starts at its first stage, not at the requested Pokémon").isEqualTo(133);
		assertThat(chain.evolvesTo()).extracting(EvolutionStage::name).containsExactly("vaporeon", "jolteon", "flareon");
		assertThat(chain.evolvesTo()).extracting(EvolutionStage::spriteUrl)
				.containsExactly("https://sprites/134.png", "https://sprites/135.png", "https://sprites/136.png");
	}

	@Test
	void theRequestedPokemonIsFetchedOnceEvenThoughItIsAlsoAStage() {
		seedBulbasaurLine();

		get(2);

		assertThat(catalog.callsTo("pokemon(2)")).isEqualTo(1);
		assertThat(catalog.callsTo("species(2)")).isEqualTo(1);
		assertThat(catalog.calls).contains("pokemon(1)", "pokemon(3)");
	}

	@Test
	void speciesWithoutAChainIsItsOwnSingleStage() {
		catalog.add(profile(128, "tauros"), species(null));

		var chain = get(128).evolutionChain();

		assertThat(chain).isEqualTo(new EvolutionStage(128, "tauros", "https://sprites/128.png", List.of()));
		assertThat(catalog.calls).noneMatch(call -> call.startsWith("chain("));
	}

	@Test
	void missingCategoryAndDescriptionStayNull() {
		catalog.add(profile(128, "tauros"), new SpeciesProfile(null, null, null));

		var detail = get(128);

		assertThat(detail.category()).isNull();
		assertThat(detail.description()).isNull();
	}

	@Test
	void unknownPokemonIsNotFound() {
		assertThatThrownBy(() -> get(99999))
				.isInstanceOf(PokemonNotFoundException.class)
				.extracting(e -> ((PokemonNotFoundException) e).pokemonId()).isEqualTo(99999);
	}

	@Test
	void aPokemonWithoutASpeciesIsNotFound() {
		// e.g. /pokemon/10001 (Deoxys Attack form) exists, /pokemon-species/10001 does not: forms are not listed (D14)
		catalog.add(profile(10001, "deoxys-attack"), species(null)).withoutSpecies(10001);

		assertThatThrownBy(() -> get(10001)).isInstanceOf(PokemonNotFoundException.class);
	}

	@Test
	void aStageThatCannotBeLoadedIsACatalogInconsistency() {
		seedBulbasaurLine();
		catalog.listedButMissing(3);

		assertThatThrownBy(() -> get(2))
				.isInstanceOf(CatalogUnavailableException.class)
				.hasMessageContaining("3")
				.hasCauseInstanceOf(PokemonNotFoundException.class);
	}

	@Test
	void aChainThatDoesNotContainThePokemonIsACatalogInconsistency() {
		catalog.add(profile(2, "ivysaur"), species(67))
				.chain(67, new EvolutionLink(133, "eevee", List.of()));

		assertThatThrownBy(() -> get(2)).isInstanceOf(CatalogUnavailableException.class).hasMessageContaining("2");
	}

	@Test
	void catalogDataThatBreaksADomainRuleIsACatalogFailureNotAServerError() {
		var typeless = new PokemonProfile(128, "tauros", null, null, Height.ofDecimetres(14), Weight.ofHectograms(884),
				List.of(), List.of(), STATS);
		catalog.add(typeless, species(null));

		assertThatThrownBy(() -> get(128))
				.isInstanceOf(CatalogUnavailableException.class)
				.hasMessageContaining("128")
				.hasCauseInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void upstreamFailureIsPropagatedAsIs() {
		seedBulbasaurLine();
		var outage = new CatalogUnavailableException("PokeAPI down", null);
		catalog.failOn(1, outage);

		assertThatThrownBy(() -> get(2)).isSameAs(outage);
	}

	@Test
	void fetchesProfileAndSpeciesConcurrently() {
		seedBulbasaurLine();
		var bothInFlight = new CyclicBarrier(2);
		catalog.onFetch(id -> {
			if (id == 2) {
				awaitAll(bothInFlight, "pokemon(2) and species(2)");
			}
		});

		assertThat(get(2).id()).isEqualTo(2);
	}

	@Test
	void fetchesTheOtherStagesConcurrently() {
		seedBulbasaurLine();
		var otherStages = Set.of(1, 3);
		var bothInFlight = new CyclicBarrier(2);
		catalog.onFetch(id -> {
			if (otherStages.contains(id)) {
				awaitAll(bothInFlight, "pokemon(1) and pokemon(3)");
			}
		});

		assertThat(get(2).evolutionChain().contains(3)).isTrue();
	}

	private static void awaitAll(CyclicBarrier barrier, String what) {
		try {
			barrier.await(2, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new AssertionError(what + " were not fetched concurrently", e);
		}
	}
}
