package com.app.poke.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.app.poke.application.port.in.RegisterUserCommand;
import com.app.poke.domain.exception.EmailTakenException;
import com.app.poke.domain.exception.InvalidUserDataException;
import com.app.poke.domain.exception.UsernameTakenException;
import com.app.poke.domain.model.Role;

class RegisterUserInteractorTest {

	private final InMemoryUserGateway users = new InMemoryUserGateway();
	private final RegisterUserInteractor interactor = new RegisterUserInteractor(users, new FakePasswordHasher());

	@Test
	void storesTheHashNeverTheRawPassword() {
		var user = interactor.register(RegisterUserCommand.of("AshKetchum", "Ash@Pallet.town", "pikachu123"));

		assertThat(user.id()).isNotNull();
		assertThat(user.passwordHash()).isEqualTo("hashed(pikachu123)");
		assertThat(users.users).singleElement().satisfies(stored -> assertThat(stored.passwordHash()).isNotEqualTo("pikachu123"));
	}

	@Test
	void keepsTheCasingTheUserTyped() {
		var user = interactor.register(RegisterUserCommand.of("AshKetchum", "Ash@Pallet.town", "pikachu123"));

		assertThat(user.username().value()).isEqualTo("AshKetchum");
		assertThat(user.email().value()).isEqualTo("Ash@Pallet.town");
	}

	@Test
	void selfRegistrationIsAlwaysAPlainUser() {
		assertThat(interactor.register(RegisterUserCommand.of("ash", "ash@pallet.town", "pikachu123")).role()).isEqualTo(Role.USER);
	}

	@Test
	void usernameTakenInAnotherCaseIsAConflict() {
		interactor.register(RegisterUserCommand.of("AshKetchum", "ash@pallet.town", "pikachu123"));

		assertThatThrownBy(() -> interactor.register(RegisterUserCommand.of("ashketchum", "other@pallet.town", "pikachu123")))
				.isInstanceOf(UsernameTakenException.class);
	}

	@Test
	void emailTakenInAnotherCaseIsAConflict() {
		interactor.register(RegisterUserCommand.of("ash", "Ash@Pallet.town", "pikachu123"));

		assertThatThrownBy(() -> interactor.register(RegisterUserCommand.of("misty", "ash@PALLET.TOWN", "starmie123")))
				.isInstanceOf(EmailTakenException.class);
	}

	@Test
	void invalidInputIsRejectedBeforeAnythingIsStored() {
		assertThatThrownBy(() -> interactor.register(RegisterUserCommand.of("ash", "ash@pallet.town", "short")))
				.isInstanceOf(InvalidUserDataException.class);
		assertThat(users.users).isEmpty();
	}
}
