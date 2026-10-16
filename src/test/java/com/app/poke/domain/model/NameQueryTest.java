package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.app.poke.domain.exception.InvalidSearchException;

class NameQueryTest {

	@Test
	void keepsWhatWasTypedTrimmedAndLowercased() {
		assertThat(new NameQuery("  ChAr ").text()).isEqualTo("char");
		assertThat(new NameQuery("ピカチュウ").text()).isEqualTo("ピカチュウ");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"char         | char",
			"Mr. Mime     | mr-mime",
			"mime jr.     | mime-jr",
			"Farfetch'd   | farfetchd",
			"Farfetch’d   | farfetchd",
			"tapu   koko  | tapu-koko",
			"ho-oh        | ho-oh",
			"-chu         | -chu" })
	void spellsTheTextLikePokeApiNames(String typed, String slug) {
		assertThat(new NameQuery(typed).slug()).isEqualTo(slug);
	}

	@Test
	void matchesAnyPartOfASlugNameIgnoringCase() {
		var query = new NameQuery("Char");

		assertThat(query.matchesSlug("charmander")).isTrue();
		assertThat(query.matchesSlug("charizard")).isTrue();
		assertThat(query.matchesSlug("Charmeleon")).isTrue();
		assertThat(query.matchesSlug("bulbasaur")).isFalse();
		assertThat(new NameQuery("mr. mime").matchesSlug("mr-mime")).isTrue();
		assertThat(new NameQuery("saur").matchesSlug("ivysaur")).isTrue();
	}

	@Test
	void acceptsFiftyCharactersButNotFiftyOne() {
		assertThat(new NameQuery("a".repeat(NameQuery.MAX_LENGTH)).text()).hasSize(50);
		assertThatThrownBy(() -> new NameQuery("a".repeat(NameQuery.MAX_LENGTH + 1)))
				.isInstanceOf(InvalidSearchException.class)
				.hasMessageContaining("50");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "...", "'", "- -" })
	void needsALetterOrDigit(String typed) {
		assertThatThrownBy(() -> new NameQuery(typed)).isInstanceOf(InvalidSearchException.class);
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> new NameQuery(null)).isInstanceOf(InvalidSearchException.class);
	}
}
