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
import com.app.poke.application.port.out.PokemonProfile;
import com.app.poke.domain.exception.PokemonNotFoundException;
import com.app.poke.domain.model.Ability;
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
	class ListEntries {

		@Test
		void returnsPokedexNumbersInOrderWithTotalCount() {
			api.stubFixture("/pokemon-species?offset=0&limit=3", "species-list-0-3.json");

			var slice = gateway.listEntries(0, 3);

			assertThat(slice.ids()).containsExactly(1, 2, 3);
			assertThat(slice.totalCount()).isEqualTo(1025);
		}

		@Test
		void forwardsOffsetAndLimit() {
			api.stub("/pokemon-species?offset=40&limit=20", 200, "{\"count\":1025,\"results\":[]}");

			assertThat(gateway.listEntries(40, 20).ids()).isEmpty();
			assertThat(api.hits("/pokemon-species?offset=40&limit=20")).isEqualTo(1);
		}

		@ParameterizedTest
		@ValueSource(strings = { "not-a-url", "{{base}}/pokemon-species/", "{{base}}/pokemon-species/abc/" })
		void malformedEntryUrlIsAnUpstreamFailure(String url) {
			api.stub("/pokemon-species?offset=0&limit=1", 200,
					"{\"count\":1,\"results\":[{\"name\":\"x\",\"url\":\"" + url + "\"}]}");

			assertThatThrownBy(() -> gateway.listEntries(0, 1)).isInstanceOf(CatalogUnavailableException.class);
		}
	}

	@Nested
	class FindPokemon {

		@Test
		void mapsRealPokeApiPayload() {
			api.stubFixture("/pokemon/1", "pokemon-1.json");

			assertThat(gateway.findPokemon(1)).isEqualTo(new PokemonProfile(1, "bulbasaur",
					"https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1.png",
					Weight.ofHectograms(69), List.of(new Ability("overgrow", false), new Ability("chlorophyll", true))));
		}

		@Test
		void ordersAbilitiesBySlotAndToleratesMissingSprite() {
			api.stub("/pokemon/7", 200, """
					{ "id": 7, "name": "squirtle", "weight": 90, "sprites": { "front_default": null },
					  "abilities": [
					    { "is_hidden": true,  "slot": 3, "ability": { "name": "rain-dish" } },
					    { "is_hidden": false, "slot": 1, "ability": { "name": "torrent" } } ] }
					""");

			var profile = gateway.findPokemon(7);

			assertThat(profile.spriteUrl()).isNull();
			assertThat(profile.abilities()).containsExactly(new Ability("torrent", false), new Ability("rain-dish", true));
		}

		@Test
		void notFoundBecomesDomainException() {
			assertThatThrownBy(() -> gateway.findPokemon(99999))
					.isInstanceOf(PokemonNotFoundException.class)
					.extracting(e -> ((PokemonNotFoundException) e).pokemonId()).isEqualTo(99999);
		}
	}

	@Nested
	class FindCategory {

		@Test
		void picksTheEnglishGenusAmongAllLanguages() {
			api.stubFixture("/pokemon-species/1", "species-1.json");

			assertThat(gateway.findCategory(1)).contains("Seed Pokémon");
		}

		@Test
		void emptyWhenNoEnglishGenus() {
			api.stub("/pokemon-species/9", 200,
					"{\"genera\":[{\"genus\":\"こうらポケモン\",\"language\":{\"name\":\"ja-hrkt\"}}]}");

			assertThat(gateway.findCategory(9)).isEmpty();
		}

		@Test
		void emptyWhenSpeciesIsUnknown() {
			assertThat(gateway.findCategory(99999)).isEmpty();
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
			api.stub("/pokemon-species?offset=0&limit=1", 200, "");

			assertThatThrownBy(() -> gateway.listEntries(0, 1)).isInstanceOf(CatalogUnavailableException.class);
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

			assertThatThrownBy(() -> unreachable.listEntries(0, 1)).isInstanceOf(CatalogUnavailableException.class);
		}
	}
}
