package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.IntFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BaseStatsTest {

	@Test
	void totalIsTheSumOfAllSix() {
		assertThat(new BaseStats(55, 55, 50, 45, 65, 55).total()).as("Eevee").isEqualTo(325);
	}

	@Test
	void extremesAreAllowed() {
		assertThat(new BaseStats(1, 1, 1, 1, 1, 1).total()).as("all minimum").isEqualTo(6);
		assertThat(new BaseStats(255, 255, 255, 255, 255, 255).total()).as("all maximum").isEqualTo(1530);
	}

	/** Each stat, built with one value and the rest valid. */
	static Stream<Arguments> eachStat() {
		return Stream.of(
				Arguments.of("hp", (IntFunction<BaseStats>) v -> new BaseStats(v, 50, 50, 50, 50, 50)),
				Arguments.of("attack", (IntFunction<BaseStats>) v -> new BaseStats(50, v, 50, 50, 50, 50)),
				Arguments.of("defense", (IntFunction<BaseStats>) v -> new BaseStats(50, 50, v, 50, 50, 50)),
				Arguments.of("specialAttack", (IntFunction<BaseStats>) v -> new BaseStats(50, 50, 50, v, 50, 50)),
				Arguments.of("specialDefense", (IntFunction<BaseStats>) v -> new BaseStats(50, 50, 50, 50, v, 50)),
				Arguments.of("speed", (IntFunction<BaseStats>) v -> new BaseStats(50, 50, 50, 50, 50, v)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("eachStat")
	void everyStatMustBeWithin1And255(String stat, IntFunction<BaseStats> withValue) {
		assertThatThrownBy(() -> withValue.apply(0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(stat);
		assertThatThrownBy(() -> withValue.apply(256)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(stat);
		assertThatCode(() -> withValue.apply(255)).doesNotThrowAnyException();
	}
}
