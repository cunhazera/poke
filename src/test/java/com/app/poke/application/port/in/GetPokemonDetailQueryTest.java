package com.app.poke.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.app.poke.domain.exception.InvalidPokemonIdException;
import com.app.poke.domain.model.PokedexNumber;

class GetPokemonDetailQueryTest {

	@Test
	void wrapsAValidPokedexNumber() {
		assertThat(GetPokemonDetailQuery.of(25).pokedexNumber()).isEqualTo(new PokedexNumber(25));
	}

	@Test
	void invalidIdIsRejectedBeforeAnyUseCaseRuns() {
		assertThatThrownBy(() -> GetPokemonDetailQuery.of(0)).isInstanceOf(InvalidPokemonIdException.class);
	}

	@Test
	void requiresANumber() {
		assertThatThrownBy(() -> new GetPokemonDetailQuery(null)).isInstanceOf(NullPointerException.class);
	}
}
