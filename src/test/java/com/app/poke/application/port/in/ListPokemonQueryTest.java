package com.app.poke.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.app.poke.domain.exception.InvalidPageRequestException;
import com.app.poke.domain.exception.InvalidSearchException;

class ListPokemonQueryTest {

	@Test
	void buildsAValidatedPageRequest() {
		assertThat(ListPokemonQuery.of(2, 10).pageRequest().offset()).isEqualTo(20);
	}

	@Test
	void invalidPaginationIsRejectedAtTheBoundary() {
		assertThatThrownBy(() -> ListPokemonQuery.of(-1, 10)).isInstanceOf(InvalidPageRequestException.class);
	}

	@Test
	void requiresAPageRequest() {
		assertThatThrownBy(() -> new ListPokemonQuery(null, null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void noSearchUnlessSomethingWasTyped() {
		assertThat(ListPokemonQuery.of(0, 20).name()).isEmpty();
		assertThat(ListPokemonQuery.of(0, 20, null).name()).isEmpty();
		assertThat(ListPokemonQuery.of(0, 20, "   ").name()).isEmpty();
	}

	@Test
	void searchTextBecomesANameQuery() {
		assertThat(ListPokemonQuery.of(0, 20, " Mr. Mime ").name()).hasValueSatisfying(q -> assertThat(q.slug()).isEqualTo("mr-mime"));
	}

	@Test
	void invalidSearchIsRejectedAtTheBoundary() {
		assertThatThrownBy(() -> ListPokemonQuery.of(0, 20, "x".repeat(51))).isInstanceOf(InvalidSearchException.class);
	}
}
