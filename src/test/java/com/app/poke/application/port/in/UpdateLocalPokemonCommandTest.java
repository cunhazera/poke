package com.app.poke.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.app.poke.domain.exception.InvalidPokemonDataException;
import com.app.poke.domain.model.ProprietaryData;

class UpdateLocalPokemonCommandTest {

	@Test
	void theVersionIsNeverNegative() {
		assertThatThrownBy(() -> new UpdateLocalPokemonCommand(1, -1, null, ProprietaryData.EMPTY))
				.isInstanceOfSatisfying(InvalidPokemonDataException.class, e -> assertThat(e.field()).isEqualTo("version"));
	}

	@Test
	void needsAStoredIdAndTheProprietaryData() {
		assertThatThrownBy(() -> new UpdateLocalPokemonCommand(0, 0, null, ProprietaryData.EMPTY))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new UpdateLocalPokemonCommand(1, 0, null, null)).isInstanceOf(NullPointerException.class);
	}
}
