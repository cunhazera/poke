package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.app.poke.domain.exception.InvalidUserDataException;

class EmailAddressTest {

	@Test
	void keepsTheCasingTheUserTyped() {
		assertThat(new EmailAddress("Ash@Pallet.town").value()).isEqualTo("Ash@Pallet.town");
	}

	@Test
	void surroundingWhitespaceIsTrimmed() {
		assertThat(new EmailAddress("  ash@pallet.town \n").value()).isEqualTo("ash@pallet.town");
	}

	@Test
	void atMost254Characters() {
		var local = "a".repeat(254 - "@pallet.town".length());
		assertThat(new EmailAddress(local + "@pallet.town").value()).hasSize(254);
		assertThatThrownBy(() -> new EmailAddress("a" + local + "@pallet.town")).isInstanceOf(InvalidUserDataException.class);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "not-an-email", "ash@pallet", "@pallet.town", "ash@", "ash @pallet.town", "ash@@pallet.town" })
	void rejectsMalformedAddressesNamingTheField(String value) {
		assertThatThrownBy(() -> new EmailAddress(value))
				.isInstanceOf(InvalidUserDataException.class)
				.extracting(e -> ((InvalidUserDataException) e).field()).isEqualTo("email");
	}
}
