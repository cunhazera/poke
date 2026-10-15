package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class WeightTest {

	@ParameterizedTest(name = "{0} hg = {1} kg")
	@CsvSource({ "69, 6.9", "0, 0.0", "1, 0.1", "9999, 999.9", "60, 6.0" })
	void convertsHectogramsToKilograms(int hectograms, double expectedKg) {
		assertThat(Weight.ofHectograms(hectograms).kilograms()).isEqualTo(expectedKg);
	}

	@Test
	void rejectsNegativeWeight() {
		assertThatThrownBy(() -> Weight.ofHectograms(-1)).isInstanceOf(IllegalArgumentException.class);
	}
}
