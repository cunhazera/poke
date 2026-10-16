package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.app.poke.domain.exception.InvalidUserDataException;

class UsernameTest {

	@ParameterizedTest
	@ValueSource(strings = { "ash", "AshKetchum", "red_1", "prof.oak", "team-rocket" })
	void acceptsSafeCharactersAndKeepsTheCasing(String value) {
		assertThat(new Username(value).value()).isEqualTo(value);
	}

	@Test
	void lengthIsThreeToFifty() {
		assertThat(new Username("a".repeat(50)).value()).hasSize(50);
		assertThatThrownBy(() -> new Username("ab")).isInstanceOf(InvalidUserDataException.class);
		assertThatThrownBy(() -> new Username("a".repeat(51))).isInstanceOf(InvalidUserDataException.class);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "has space", "ash!", "ash@home", " ash", "açaí" })
	void rejectsAnythingElseNamingTheField(String value) {
		assertThatThrownBy(() -> new Username(value))
				.isInstanceOf(InvalidUserDataException.class)
				.extracting(e -> ((InvalidUserDataException) e).field()).isEqualTo("username");
	}
}
