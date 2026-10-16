package com.app.poke.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.app.poke.application.exception.InvalidCredentialsException;
import com.app.poke.application.port.in.AccessToken;
import com.app.poke.application.port.in.AuthenticateUserCommand;
import com.app.poke.application.port.out.NewUser;
import com.app.poke.application.port.out.TokenIssuer;
import com.app.poke.domain.model.EmailAddress;
import com.app.poke.domain.model.Role;
import com.app.poke.domain.model.User;
import com.app.poke.domain.model.Username;

class AuthenticateUserInteractorTest {

	private final InMemoryUserGateway users = new InMemoryUserGateway();
	private final FakePasswordHasher hasher = new FakePasswordHasher();
	private final List<User> tokensIssuedFor = new ArrayList<>();
	private final TokenIssuer issuer = user -> {
		tokensIssuedFor.add(user);
		return new AccessToken("token-for-" + user.id(), Duration.ofHours(1));
	};
	private final AuthenticateUserInteractor interactor = new AuthenticateUserInteractor(users, hasher, issuer);

	@BeforeEach
	void registerAsh() {
		users.create(new NewUser(new Username("AshKetchum"), new EmailAddress("Ash@Pallet.town"), "hashed(pikachu123)", Role.USER));
	}

	@ParameterizedTest(name = "login = {0}")
	@ValueSource(strings = { "AshKetchum", "ashketchum", "ASHKETCHUM", "Ash@Pallet.town", "ash@pallet.town" })
	void logsInWithUsernameOrEmailInAnyCase(String login) {
		var token = interactor.authenticate(new AuthenticateUserCommand(login, "pikachu123"));

		assertThat(token.value()).isEqualTo("token-for-1");
		assertThat(token.validity()).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void wrongPasswordIsRejectedAndNoTokenIsIssued() {
		assertThatThrownBy(() -> interactor.authenticate(new AuthenticateUserCommand("AshKetchum", "pikachu124")))
				.isInstanceOf(InvalidCredentialsException.class);
		assertThat(tokensIssuedFor).isEmpty();
	}

	@Test
	void unknownUserAndWrongPasswordAreIndistinguishable() {
		var unknown = catchMessage(new AuthenticateUserCommand("gary", "pikachu123"));
		var wrongPassword = catchMessage(new AuthenticateUserCommand("AshKetchum", "wrong-password"));

		assertThat(unknown).isEqualTo(wrongPassword);
	}

	@Test
	void anUnknownUserStillCostsAPasswordCheck() {
		// Without it, a fast 401 would reveal that the user doesn't exist (timing-based user enumeration).
		catchMessage(new AuthenticateUserCommand("gary", "pikachu123"));

		assertThat(hasher.checkedAgainst).containsExactly("null");
	}

	@Test
	void passwordsAreCaseSensitive() {
		assertThatThrownBy(() -> interactor.authenticate(new AuthenticateUserCommand("AshKetchum", "PIKACHU123")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	private String catchMessage(AuthenticateUserCommand command) {
		try {
			interactor.authenticate(command);
			throw new AssertionError("expected InvalidCredentialsException");
		} catch (InvalidCredentialsException e) {
			return e.getMessage();
		}
	}
}
