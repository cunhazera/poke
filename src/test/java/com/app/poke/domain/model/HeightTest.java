package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HeightTest {

	@ParameterizedTest(name = "{0} dm = {1} m")
	@CsvSource({ "7, 0.7", "3, 0.3", "0, 0.0", "20, 2.0", "1000, 100.0" })
	void convertsDecimetresToMetres(int decimetres, double expectedMetres) {
		assertThat(Height.ofDecimetres(decimetres).metres()).isEqualTo(expectedMetres);
	}

	@Test
	void rejectsNegativeHeight() {
		assertThatThrownBy(() -> Height.ofDecimetres(-1)).isInstanceOf(IllegalArgumentException.class);
	}
}
