package com.app.poke.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.app.poke.domain.exception.InvalidUserDataException;

class RegisterUserCommandTest {

	@Test
	void buildsValidatedValues() {
		var command = RegisterUserCommand.of("ash", " ash@pallet.town ", "pikachu123");

		assertThat(command.username().value()).isEqualTo("ash");
		assertThat(command.email().value()).isEqualTo("ash@pallet.town");
	}

	@Test
	void reportsTheFirstInvalidFieldInFormOrder() {
		assertThatThrownBy(() -> RegisterUserCommand.of("a", "bad", "short"))
				.isInstanceOf(InvalidUserDataException.class)
				.extracting(e -> ((InvalidUserDataException) e).field()).isEqualTo("username");
	}

	@Test
	void theCommandDoesNotPrintThePassword() {
		assertThat(RegisterUserCommand.of("ash", "ash@pallet.town", "pikachu123").toString()).doesNotContain("pikachu123");
	}
}
