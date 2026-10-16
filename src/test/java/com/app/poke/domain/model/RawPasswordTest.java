package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import com.app.poke.domain.exception.InvalidUserDataException;

class RawPasswordTest {

	@Test
	void eightToSeventyTwoBytes() {
		assertThat(new RawPassword("pikachu1").value()).isEqualTo("pikachu1");
		assertThat(new RawPassword("p".repeat(72)).value()).hasSize(72);
		assertThatThrownBy(() -> new RawPassword("pikachu")).isInstanceOf(InvalidUserDataException.class);
		assertThatThrownBy(() -> new RawPassword("p".repeat(73))).isInstanceOf(InvalidUserDataException.class);
	}

	@Test
	void theLimitIsInBytesBecauseBcryptIgnoresEverythingPast72() {
		var thirtySevenTwoByteChars = "é".repeat(37); // 37 characters, 74 bytes in UTF-8

		assertThatThrownBy(() -> new RawPassword(thirtySevenTwoByteChars))
				.isInstanceOf(InvalidUserDataException.class)
				.hasMessageContaining("72 bytes");
	}

	@ParameterizedTest
	@NullAndEmptySource
	void isRequired(String value) {
		assertThatThrownBy(() -> new RawPassword(value))
				.isInstanceOf(InvalidUserDataException.class)
				.extracting(e -> ((InvalidUserDataException) e).field()).isEqualTo("password");
	}

	@Test
	void neverAppearsInToStringOrInErrorMessages() {
		assertThat(new RawPassword("pikachu123").toString()).doesNotContain("pikachu123");
		assertThatThrownBy(() -> new RawPassword("short")).message().doesNotContain("short");
	}
}
