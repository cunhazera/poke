package com.app.poke.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class PathIdsTest {

	@Test
	void parsesPlainDigits() {
		assertThat(PathIds.asInt("25")).contains(25);
		assertThat(PathIds.asInt("2147483647")).contains(Integer.MAX_VALUE);
		assertThat(PathIds.asLong("999999999999999999")).contains(999_999_999_999_999_999L);
		assertThat(PathIds.asInt("0")).as("positivity is the caller's rule").contains(0);
	}

	@Test
	void anIntMustFitInAnInt() {
		assertThat(PathIds.asInt("2147483648")).isEmpty();
		assertThat(PathIds.asLong("2147483648")).contains(2_147_483_648L);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " 1", "+1", "-1", "1.5", "1e3", "0x1F", "abc", "1234567890123456789" })
	void rejectsAnythingThatIsNotPlainDigits(String value) {
		assertThat(PathIds.asLong(value)).isEmpty();
		assertThat(PathIds.asInt(value)).isEmpty();
	}
}
