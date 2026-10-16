package com.app.poke.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.app.poke.application.port.out.NewUser;
import com.app.poke.domain.exception.UserNotFoundException;
import com.app.poke.domain.model.EmailAddress;
import com.app.poke.domain.model.Role;
import com.app.poke.domain.model.Username;

class GetCurrentUserInteractorTest {

	private final InMemoryUserGateway users = new InMemoryUserGateway();
	private final GetCurrentUserInteractor interactor = new GetCurrentUserInteractor(users);

	@Test
	void returnsTheUserTheTokenBelongsTo() {
		var ash = users.create(new NewUser(new Username("ash"), new EmailAddress("ash@pallet.town"), "h", Role.USER));

		assertThat(interactor.get(ash.id())).isEqualTo(ash);
	}

	@Test
	void aTokenForADeletedUserFindsNoOne() {
		assertThatThrownBy(() -> interactor.get(42)).isInstanceOf(UserNotFoundException.class);
	}
}
