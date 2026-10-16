package com.app.poke.application.usecase;

import java.util.Objects;

import com.app.poke.application.port.in.RegisterUserCommand;
import com.app.poke.application.port.in.RegisterUserUseCase;
import com.app.poke.application.port.out.NewUser;
import com.app.poke.application.port.out.PasswordHasher;
import com.app.poke.application.port.out.UserGateway;
import com.app.poke.domain.model.Role;
import com.app.poke.domain.model.User;

/**
 * Registers a plain user. Uniqueness is left to the gateway (a database unique index), not checked first: a
 * check-then-insert would race with a concurrent registration.
 */
public final class RegisterUserInteractor implements RegisterUserUseCase {

	private final UserGateway users;
	private final PasswordHasher hasher;

	public RegisterUserInteractor(UserGateway users, PasswordHasher hasher) {
		this.users = Objects.requireNonNull(users, "users");
		this.hasher = Objects.requireNonNull(hasher, "hasher");
	}

	@Override
	public User register(RegisterUserCommand command) {
		return users.create(new NewUser(command.username(), command.email(), hasher.hash(command.password()), Role.USER));
	}
}
