package com.app.poke.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.app.poke.domain.exception.InvalidPageRequestException;

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
		assertThatThrownBy(() -> new ListPokemonQuery(null)).isInstanceOf(NullPointerException.class);
	}
}
