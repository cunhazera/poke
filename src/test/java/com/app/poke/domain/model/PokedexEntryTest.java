package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.app.poke.domain.exception.InvalidPokemonDataException;

class PokedexEntryTest {

	private static final BaseStats STATS = new BaseStats(45, 49, 49, 65, 65, 45);

	private static PokedexEntry named(String name) {
		return new PokedexEntry(name, null, null, Height.ofDecimetres(7), Weight.ofHectograms(69), null, null,
				List.of("grass"), List.of(), STATS);
	}

	private static PokedexEntry withTexts(String category, String description) {
		return new PokedexEntry("bulbasaur", category, description, Height.ofDecimetres(7), Weight.ofHectograms(69), null,
				null, List.of("grass"), List.of(), STATS);
	}

	private static PokedexEntry withImages(String spriteUrl, String imageUrl) {
		return new PokedexEntry("bulbasaur", null, null, Height.ofDecimetres(7), Weight.ofHectograms(69), spriteUrl,
				imageUrl, List.of("grass"), List.of(), STATS);
	}

	private static PokedexEntry withAbilities(List<Ability> abilities) {
		return new PokedexEntry("bulbasaur", null, null, Height.ofDecimetres(7), Weight.ofHectograms(69), null, null,
				List.of("grass"), abilities, STATS);
	}

	private static String fieldOf(Runnable creation) {
		try {
			creation.run();
			throw new AssertionError("expected InvalidPokemonDataException");
		} catch (InvalidPokemonDataException e) {
			return e.field();
		}
	}

	private static PokedexEntry withTypes(List<String> types) {
		return new PokedexEntry("bulbasaur", null, null, Height.ofDecimetres(7), Weight.ofHectograms(69), null, null, types,
				List.of(), STATS);
	}

	@Test
	void textsAndImagesAreOptional() {
		var entry = withTypes(List.of("grass"));

		assertThat(entry.category()).isNull();
		assertThat(entry.description()).isNull();
		assertThat(entry.spriteUrl()).isNull();
	}

	@Test
	void hasOneOrTwoTypes() {
		assertThat(withTypes(List.of("grass", "poison")).types()).hasSize(2);
		assertThatThrownBy(() -> withTypes(List.of())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> withTypes(List.of("fire", "water", "grass"))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void atMostThreeAbilitiesTheSchemaHasSlotsFor() {
		var four = List.of(new Ability("a", false), new Ability("b", false), new Ability("c", false), new Ability("d", true));

		assertThatThrownBy(() -> new PokedexEntry("x", null, null, Height.ofDecimetres(1), Weight.ofHectograms(1), null,
				null, List.of("normal"), four, STATS)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void collectionsAreDefensivelyCopied() {
		var types = new ArrayList<>(List.of("grass"));
		var entry = withTypes(types);

		types.add("poison");

		assertThat(entry.types()).containsExactly("grass");
	}

	@Test
	void requiresANameAndTheMeasurements() {
		assertThatThrownBy(() -> new PokedexEntry(" ", null, null, Height.ofDecimetres(1), Weight.ofHectograms(1), null,
				null, List.of("normal"), List.of(), STATS)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PokedexEntry("x", null, null, null, Weight.ofHectograms(1), null, null,
				List.of("normal"), List.of(), STATS)).isInstanceOf(NullPointerException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "mr-mime", "porygon-z", "nidoran-f", "farfetchd", "missingno", "a" })
	void namesAreLowercaseSlugsLikeTheCatalogs(String name) {
		assertThat(named(name).name()).isEqualTo(name);
	}

	@ParameterizedTest
	@ValueSource(strings = { "Pikachu", "mr mime", "-pikachu", " pikachu", "pikachu!", "" })
	void otherNamesAreRejectedNamingTheField(String name) {
		assertThat(fieldOf(() -> named(name))).isEqualTo("name");
	}

	@Test
	void namesAreAtMost100Characters() {
		assertThat(named("a".repeat(100)).name()).hasSize(100);
		assertThat(fieldOf(() -> named("a".repeat(101)))).isEqualTo("name");
		assertThat(fieldOf(() -> named(null))).isEqualTo("name");
	}

	@Test
	void typesAreCanonicalAndDistinct() {
		assertThat(fieldOf(() -> withTypes(List.of("fyre")))).isEqualTo("types");
		assertThat(fieldOf(() -> withTypes(List.of("fire", "fire")))).isEqualTo("types");
		assertThat(fieldOf(() -> withTypes(List.of()))).isEqualTo("types");
	}

	@Test
	void abilitiesHaveDistinctSlugNames() {
		assertThat(withAbilities(List.of(new Ability("overgrow", false), new Ability("chlorophyll", true))).abilities())
				.hasSize(2);
		assertThat(fieldOf(() -> withAbilities(List.of(new Ability("overgrow", false), new Ability("overgrow", true)))))
				.isEqualTo("abilities");
		assertThat(fieldOf(() -> withAbilities(List.of(new Ability("Over Grow", false))))).isEqualTo("abilities");
		assertThat(fieldOf(() -> withAbilities(List.of(new Ability("a".repeat(51), false))))).isEqualTo("abilities");
	}

	@Test
	void categoryAndDescriptionAreNeverBlankOrTooLong() {
		assertThat(fieldOf(() -> withTexts(" ", null))).isEqualTo("category");
		assertThat(fieldOf(() -> withTexts("x".repeat(101), null))).isEqualTo("category");
		assertThat(fieldOf(() -> withTexts(null, ""))).isEqualTo("description");
		assertThat(fieldOf(() -> withTexts(null, "x".repeat(2001)))).isEqualTo("description");
		assertThat(withTexts("x".repeat(100), "x".repeat(2000)).description()).hasSize(2000);
	}

	@ParameterizedTest
	@ValueSource(strings = { "ftp://sprites/1.png", "javascript:alert(1)", "/relative.png", "not a url", "https://" })
	void imagesAreAbsoluteHttpUrls(String url) {
		assertThat(fieldOf(() -> withImages(url, null))).isEqualTo("spriteUrl");
		assertThat(fieldOf(() -> withImages(null, url))).isEqualTo("imageUrl");
	}

	@Test
	void imageUrlsFitTheSchema() {
		var longUrl = "https://example.com/" + "a".repeat(481);
		assertThat(fieldOf(() -> withImages(longUrl, null))).isEqualTo("spriteUrl");
		assertThat(withImages("http://example.com/1.png", "https://example.com/1.png").imageUrl())
				.isEqualTo("https://example.com/1.png");
	}
}
