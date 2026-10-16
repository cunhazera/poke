package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.app.poke.domain.exception.InvalidPokemonDataException;

class ProprietaryDataTest {

	private static ProprietaryData withNames(Map<String, String> names) {
		return new ProprietaryData(names, null, null, null, Set.of());
	}

	private static ProprietaryData withTags(Set<String> tags) {
		return new ProprietaryData(Map.of(), null, null, null, tags);
	}

	private static String fieldOf(Runnable creation) {
		try {
			creation.run();
			throw new AssertionError("expected InvalidPokemonDataException");
		} catch (InvalidPokemonDataException e) {
			return e.field();
		}
	}

	@Test
	void emptyIsAValidStartingPoint() {
		assertThat(ProprietaryData.EMPTY.localizedNames()).isEmpty();
		assertThat(ProprietaryData.EMPTY.tags()).isEmpty();
		assertThat(ProprietaryData.EMPTY.coordinates()).isNull();
	}

	@Test
	void keepsEverythingInAStableSortedOrder() {
		var data = new ProprietaryData(Map.of("pt-BR", "Bulbassauro", "ja", "フシギダネ", "fr", "Bulbizarre"), "Kanto",
				"grassland", new Coordinates(new BigDecimal("35.6762"), new BigDecimal("139.6503")), Set.of("starter", "gen-1"));

		assertThat(data.localizedNames().keySet()).containsExactly("fr", "ja", "pt-BR");
		assertThat(data.tags()).containsExactly("gen-1", "starter");
	}

	@Test
	void isImmutableEvenIfTheSourcesChange() {
		var names = new HashMap<>(Map.of("ja", "フシギダネ"));
		var tags = new HashSet<>(Set.of("starter"));
		var data = new ProprietaryData(names, null, null, null, tags);

		names.put("fr", "Bulbizarre");
		tags.add("legendary");

		assertThat(data.localizedNames()).containsOnlyKeys("ja");
		assertThat(data.tags()).containsExactly("starter");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Portuguese", "PT", "pt_BR", "p", "" })
	void languagesAreBcp47Tags(String language) {
		assertThat(fieldOf(() -> withNames(Map.of(language, "x")))).isEqualTo("localizedNames");
	}

	@Test
	void localizedNamesAreNonBlankAndAtMost100Characters() {
		assertThat(fieldOf(() -> withNames(Map.of("ja", " ")))).isEqualTo("localizedNames");
		assertThat(fieldOf(() -> withNames(Map.of("ja", "x".repeat(101))))).isEqualTo("localizedNames");
		assertThat(withNames(Map.of("ja", "x".repeat(100))).localizedNames()).hasSize(1);
	}

	@ParameterizedTest
	@ValueSource(strings = { "Starter", "has space", "-leading-dash", "" })
	void tagsAreLowercaseSlugs(String tag) {
		assertThat(fieldOf(() -> withTags(Set.of(tag)))).isEqualTo("tags");
	}

	@Test
	void tagsAreAtMost50Characters() {
		assertThat(fieldOf(() -> withTags(Set.of("a".repeat(51))))).isEqualTo("tags");
	}

	@Test
	void regionAndHabitatAreOptionalButNeverBlankOrLong() {
		assertThat(fieldOf(() -> new ProprietaryData(Map.of(), " ", null, null, Set.of()))).isEqualTo("region");
		assertThat(fieldOf(() -> new ProprietaryData(Map.of(), null, "x".repeat(51), null, Set.of()))).isEqualTo("habitat");
	}
}
