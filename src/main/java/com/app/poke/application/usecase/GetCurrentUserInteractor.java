package com.app.poke.application.usecase;

import java.util.Objects;

import com.app.poke.application.port.in.GetCurrentUserUseCase;
import com.app.poke.application.port.out.UserGateway;
import com.app.poke.domain.exception.UserNotFoundException;
import com.app.poke.domain.model.User;

public final class GetCurrentUserInteractor implements GetCurrentUserUseCase {

	private final UserGateway users;

	public GetCurrentUserInteractor(UserGateway users) {
		this.users = Objects.requireNonNull(users, "users");
	}

	@Override
	public User get(long userId) {
		return users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
	}
}
