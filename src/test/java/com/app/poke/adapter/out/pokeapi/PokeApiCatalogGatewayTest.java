package com.app.poke.adapter.out.pokeapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.app.poke.application.exception.CatalogUnavailableException;
import com.app.poke.application.port.out.CatalogEntry;
import com.app.poke.application.port.out.EvolutionLink;
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.application.port.out.SpeciesProfile;
import com.app.poke.domain.exception.PokemonNotFoundException;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.BaseStats;
import com.app.poke.domain.model.Height;
import com.app.poke.domain.model.Weight;
import com.app.poke.support.FakePokeApiServer;

/** Exercises the real HTTP client against a fake PokeAPI server: status codes, JSON mapping, timeouts. */
class PokeApiCatalogGatewayTest {

	private final FakePokeApiServer api = FakePokeApiServer.start();
	private final PokeApiCatalogGateway gateway = new PokeApiCatalogGateway(client(api.baseUrl(), Duration.ofMillis(500)));

	static RestClient client(String baseUrl, Duration readTimeout) {
		var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build());
		factory.setReadTimeout(readTimeout);
		return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
	}

	@AfterEach
	void stop() {
		api.close();
	}

	@Nested
	class ListAllEntries {

		private static final String INDEX = "/pokemon-species?limit=" + PokeApiCatalogGateway.INDEX_LIMIT;

		@Test
		void returnsEverySpeciesNumberAndNameInPokedexOrder() {
			api.stubFixture(INDEX, "species-index.json");

			assertThat(gateway.listAllEntries()).containsExactly(
					new CatalogEntry(1, "bulbasaur"), new CatalogEntry(2, "ivysaur"), new CatalogEntry(3, "venusaur"));
		}

		@Test
		void ordersByNumberWhateverOrderPokeApiSends() {
			api.stub(INDEX, 200, """
					{"count":2,"results":[{"name":"ivysaur","url":"{{base}}/pokemon-species/2/"},
					                      {"name":"bulbasaur","url":"{{base}}/pokemon-species/1/"}]}""");

			assertThat(gateway.listAllEntries()).extracting(CatalogEntry::id).containsExactly(1, 2);
		}

		@Test
		void anIncompleteIndexIsAnUpstreamFailureNotAShorterCatalog() {
			api.stub(INDEX, 200, """
					{"count":1025,"next":"{{base}}/pokemon-species?offset=1&limit=1",
					 "results":[{"name":"bulbasaur","url":"{{base}}/pokemon-species/1/"}]}""");

			assertThatThrownBy(() -> gateway.listAllEntries()).isInstanceOf(CatalogUnavailableException.class)
					.hasMessageContaining("species index");
		}

		@ParameterizedTest
		@ValueSource(strings = { "not-a-url", "{{base}}/pokemon-species/", "{{base}}/pokemon-species/abc/" })
		void malformedEntryUrlIsAnUpstreamFailure(String url) {
			api.stub(INDEX, 200, "{\"count\":1,\"results\":[{\"name\":\"x\",\"url\":\"" + url + "\"}]}");

			assertThatThrownBy(() -> gateway.listAllEntries()).isInstanceOf(CatalogUnavailableException.class);
		}

		@Test
		void entryWithoutANameIsAnUpstreamFailure() {
			api.stub(INDEX, 200, "{\"count\":1,\"results\":[{\"url\":\"{{base}}/pokemon-species/1/\"}]}");

			assertThatThrownBy(() -> gateway.listAllEntries()).isInstanceOf(CatalogUnavailableException.class);
		}
	}

	@Nested
	class FindPokemon {

		private static final String SQUIRTLE_STATS = """
				[ { "base_stat": 44, "stat": { "name": "hp" } }, { "base_stat": 48, "stat": { "name": "attack" } },
				  { "base_stat": 65, "stat": { "name": "defense" } }, { "base_stat": 50, "stat": { "name": "special-attack" } },
				  { "base_stat": 64, "stat": { "name": "special-defense" } }, { "base_stat": 43, "stat": { "name": "speed" } } ]""";

		/** Squirtle with deliberately shuffled slots (types and abilities are not in slot order). */
		private static String squirtle(String stats) {
			return """
					{ "id": 7, "name": "squirtle", "height": 5, "weight": 90,
					  "sprites": { "front_default": "s.png", "other": { "official-artwork": { "front_default": "a.png" } } },
					  "types": [ { "slot": 2, "type": { "name": "ice" } }, { "slot": 1, "type": { "name": "water" } } ],
					  "abilities": [
					    { "is_hidden": true,  "slot": 3, "ability": { "name": "rain-dish" } },
					    { "is_hidden": false, "slot": 1, "ability": { "name": "torrent" } } ],
					  "stats": %s }
					""".formatted(stats);
		}

		@Test
		void mapsRealPokeApiPayload() {
			api.stubFixture("/pokemon/1", "pokemon-1.json");

			assertThat(gateway.findPokemon(1)).isEqualTo(new PokemonProfile(1, "bulbasaur",
					"https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1.png",
					"https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/official-artwork/1.png",
					Height.ofDecimetres(7), Weight.ofHectograms(69), List.of("grass", "poison"),
					List.of(new Ability("overgrow", false), new Ability("chlorophyll", true)),
					new BaseStats(45, 49, 49, 65, 65, 45)));
		}

		@Test
		void ordersTypesAndAbilitiesBySlotAndMapsStatsByNameNotPosition() {
			api.stub("/pokemon/7", 200, squirtle("""
					[ { "base_stat": 43, "stat": { "name": "speed" } },
					  { "base_stat": 44, "stat": { "name": "hp" } },
					  { "base_stat": 64, "stat": { "name": "special-defense" } },
					  { "base_stat": 48, "stat": { "name": "attack" } },
					  { "base_stat": 50, "stat": { "name": "special-attack" } },
					  { "base_stat": 65, "stat": { "name": "defense" } } ]"""));

			var profile = gateway.findPokemon(7);

			assertThat(profile.stats()).isEqualTo(new BaseStats(44, 48, 65, 50, 64, 43));
			assertThat(profile.types()).containsExactly("water", "ice");
			assertThat(profile.abilities()).containsExactly(new Ability("torrent", false), new Ability("rain-dish", true));
		}

		@Test
		void toleratesMissingSpriteAndArtwork() {
			api.stub("/pokemon/7", 200, squirtle(SQUIRTLE_STATS).replace("\"sprites\": { \"front_default\": \"s.png\", "
					+ "\"other\": { \"official-artwork\": { \"front_default\": \"a.png\" } } }", "\"sprites\": {}"));

			var profile = gateway.findPokemon(7);

			assertThat(profile.spriteUrl()).isNull();
			assertThat(profile.imageUrl()).isNull();
		}

		@Test
		void missingStatIsAnUpstreamFailure() {
			api.stub("/pokemon/7", 200, squirtle("[ { \"base_stat\": 44, \"stat\": { \"name\": \"hp\" } } ]"));

			assertThatThrownBy(() -> gateway.findPokemon(7)).isInstanceOf(CatalogUnavailableException.class)
					.hasMessageContaining("attack");
		}

		@Test
		void outOfRangeStatIsAnUpstreamFailureNotA500() {
			api.stub("/pokemon/7", 200, squirtle(SQUIRTLE_STATS.replace("\"base_stat\": 44", "\"base_stat\": 0")));

			assertThatThrownBy(() -> gateway.findPokemon(7)).isInstanceOf(CatalogUnavailableException.class);
		}

		@Test
		void notFoundBecomesDomainException() {
			assertThatThrownBy(() -> gateway.findPokemon(99999))
					.isInstanceOf(PokemonNotFoundException.class)
					.extracting(e -> ((PokemonNotFoundException) e).pokemonId()).isEqualTo(99999);
		}
	}

	@Nested
	class FindSpecies {

		@Test
		void picksTheEnglishGenusAmongAllLanguages() {
			api.stubFixture("/pokemon-species/1", "species-1.json");

			assertThat(gateway.findSpecies(1)).map(SpeciesProfile::category).contains("Seed Pokémon");
		}

		@Test
		void speciesWithoutEnglishGenusIsFoundWithNoCategory() {
			api.stub("/pokemon-species/9", 200,
					"{\"genera\":[{\"genus\":\"こうらポケモン\",\"language\":{\"name\":\"ja-hrkt\"}}]}");

			assertThat(gateway.findSpecies(9)).hasValueSatisfying(species -> assertThat(species.category()).isNull());
		}

		@Test
		void emptyWhenSpeciesIsUnknown() {
			assertThat(gateway.findSpecies(99999)).isEmpty();
		}

		@Test
		void descriptionIsTheMostRecentEnglishFlavorText() {
			api.stubFixture("/pokemon-species/1", "species-1.json");

			assertThat(gateway.findSpecies(1).orElseThrow().description()).isEqualTo(
					"While it is young, it uses the nutrients that are stored in the seed on its back in order to grow.");
		}

		@Test
		void oldFlavorTextIsNormalizedToSingleSpaces() {
			api.stub("/pokemon-species/1", 200, flavorTexts(
					"{ \"flavor_text\": \"A strange seed was\\nplanted on its\\nback at birth.\\fThe plant sprouts\", "
							+ "\"language\": { \"name\": \"en\" } }"));

			assertThat(gateway.findSpecies(1).orElseThrow().description())
					.isEqualTo("A strange seed was planted on its back at birth. The plant sprouts");
		}

		@Test
		void softHyphensAtLineBreaksAreJoined() {
			api.stub("/pokemon-species/1", 200, flavorTexts(
					"{ \"flavor_text\": \"It stores ener\\u00ad\\ngy in its  body.\", \"language\": { \"name\": \"en\" } }"));

			assertThat(gateway.findSpecies(1).orElseThrow().description()).isEqualTo("It stores energy in its body.");
		}

		@Test
		void descriptionIsNullWithoutEnglishText() {
			api.stub("/pokemon-species/1", 200, flavorTexts(
					"{ \"flavor_text\": \"Texte\", \"language\": { \"name\": \"fr\" } }"));

			assertThat(gateway.findSpecies(1).orElseThrow().description()).isNull();
		}

		@Test
		void evolutionChainIdComesFromTheChainUrlNotThePokedexNumber() {
			api.stubFixture("/pokemon-species/133", "species-133.json");

			assertThat(gateway.findSpecies(133).orElseThrow().evolutionChainId()).isEqualTo(67);
		}

		@Test
		void speciesWithoutAChainHasNoChainId() {
			api.stub("/pokemon-species/1", 200, flavorTexts(""));

			assertThat(gateway.findSpecies(1).orElseThrow().evolutionChainId()).isNull();
		}

		private static String flavorTexts(String entries) {
			return "{ \"genera\": [], \"flavor_text_entries\": [" + entries + "] }";
		}

		@Test
		void speciesFailureIsCatalogUnavailable() {
			api.stub("/pokemon-species/1", 503, "{}");

			assertThatThrownBy(() -> gateway.findSpecies(1)).isInstanceOf(CatalogUnavailableException.class);
		}
	}

	@Nested
	class FindEvolutionChain {

		@Test
		void mapsALinearChain() {
			api.stubFixture("/evolution-chain/1", "evolution-chain-1.json");

			assertThat(gateway.findEvolutionChain(1)).isEqualTo(new EvolutionLink(1, "bulbasaur",
					List.of(new EvolutionLink(2, "ivysaur", List.of(EvolutionLink.single(3, "venusaur"))))));
		}

		@Test
		void mapsABranchingChainInCatalogOrder() {
			api.stubFixture("/evolution-chain/67", "evolution-chain-67.json");

			var eevee = gateway.findEvolutionChain(67);

			assertThat(eevee.speciesId()).isEqualTo(133);
			assertThat(eevee.evolvesTo()).extracting(EvolutionLink::speciesId)
					.containsExactly(134, 135, 136, 196, 197, 470, 471, 700);
			assertThat(eevee.evolvesTo()).allSatisfy(next -> assertThat(next.evolvesTo()).isEmpty());
		}

		@Test
		void mapsASingleStageChain() {
			api.stubFixture("/evolution-chain/63", "evolution-chain-63.json");

			assertThat(gateway.findEvolutionChain(63)).isEqualTo(EvolutionLink.single(128, "tauros"));
		}

		@Test
		void unknownChainIsAnUpstreamInconsistency() {
			assertThatThrownBy(() -> gateway.findEvolutionChain(99999)).isInstanceOf(CatalogUnavailableException.class);
		}

		@Test
		void chainFailureIsCatalogUnavailable() {
			api.stub("/evolution-chain/1", 500, "{}");

			assertThatThrownBy(() -> gateway.findEvolutionChain(1)).isInstanceOf(CatalogUnavailableException.class);
		}
	}

	@Nested
	class UpstreamFailures {

		@ParameterizedTest
		@ValueSource(ints = { 500, 502, 503, 429 })
		void errorStatusesBecomeCatalogUnavailable(int status) {
			api.stub("/pokemon/1", status, "{\"message\":\"boom\"}");

			assertThatThrownBy(() -> gateway.findPokemon(1)).isInstanceOf(CatalogUnavailableException.class);
		}

		@Test
		void malformedJsonBecomesCatalogUnavailable() {
			api.stub("/pokemon/1", 200, "{ this is not json");

			assertThatThrownBy(() -> gateway.findPokemon(1)).isInstanceOf(CatalogUnavailableException.class);
		}

		@Test
		void emptyBodyBecomesCatalogUnavailable() {
			api.stub("/pokemon-species?limit=" + PokeApiCatalogGateway.INDEX_LIMIT, 200, "");

			assertThatThrownBy(() -> gateway.listAllEntries()).isInstanceOf(CatalogUnavailableException.class);
		}

		@Test
		void slowResponseTimesOut() {
			api.stub("/pokemon/1", 200, FakePokeApiServer.fixture("pokemon-1.json"), Duration.ofSeconds(3));

			assertThatThrownBy(() -> gateway.findPokemon(1)).isInstanceOf(CatalogUnavailableException.class);
		}

		@Test
		void unreachableServerBecomesCatalogUnavailable() {
			var deadUrl = api.baseUrl();
			api.close();
			var unreachable = new PokeApiCatalogGateway(client(deadUrl, Duration.ofMillis(500)));

			assertThatThrownBy(() -> unreachable.listAllEntries()).isInstanceOf(CatalogUnavailableException.class);
		}
	}
}
