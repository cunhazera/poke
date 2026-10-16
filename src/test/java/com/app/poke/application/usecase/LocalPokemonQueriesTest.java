package com.app.poke.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.app.poke.application.port.in.ListLocalPokemonQuery;
import com.app.poke.domain.exception.LocalPokemonNotFoundException;
import com.app.poke.domain.model.LocalPokemon;
import com.app.poke.domain.model.ProprietaryData;

/** The two read use cases over the local store. */
class LocalPokemonQueriesTest {

	private final InMemoryLocalPokemonStore store = new InMemoryLocalPokemonStore();

	private LocalPokemon stored(Integer pokedexNumber, String name) {
		return store.insert(new LocalPokemon(null, pokedexNumber, LocalPokemonFixtures.entry(name, null),
				ProprietaryData.EMPTY, pokedexNumber == null ? null : Instant.EPOCH, 0));
	}

	@Test
	void listsInPokedexOrderWithLocalOnlyPokemonLast() {
		stored(25, "pikachu");
		stored(null, "homebrewmon");
		stored(1, "bulbasaur");

		var page = new ListLocalPokemonInteractor(store).list(ListLocalPokemonQuery.of(0, 10));

		assertThat(page.items()).extracting(p -> p.entry().name()).containsExactly("bulbasaur", "pikachu", "homebrewmon");
		assertThat(page.totalItems()).isEqualTo(3);
	}

	@Test
	void pagesThroughTheStore() {
		for (int n = 1; n <= 5; n++) {
			stored(n, "mon" + n);
		}

		var page = new ListLocalPokemonInteractor(store).list(ListLocalPokemonQuery.of(1, 2));

		assertThat(page.items()).extracting(LocalPokemon::pokedexNumber).containsExactly(3, 4);
		assertThat(page.totalPages()).isEqualTo(3);
	}

	@Test
	void getsOneById() {
		var pikachu = stored(25, "pikachu");

		assertThat(new GetLocalPokemonInteractor(store).get(pikachu.id())).isEqualTo(pikachu);
	}

	@Test
	void anUnknownIdIsNotFound() {
		assertThatThrownBy(() -> new GetLocalPokemonInteractor(store).get(404)).isInstanceOf(LocalPokemonNotFoundException.class);
	}
}
