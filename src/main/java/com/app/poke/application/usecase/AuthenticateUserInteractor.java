package com.app.poke.application.usecase;

import java.util.Objects;

import com.app.poke.application.exception.InvalidCredentialsException;
import com.app.poke.application.port.in.AccessToken;
import com.app.poke.application.port.in.AuthenticateUserCommand;
import com.app.poke.application.port.in.AuthenticateUserUseCase;
import com.app.poke.application.port.out.PasswordHasher;
import com.app.poke.application.port.out.TokenIssuer;
import com.app.poke.application.port.out.UserGateway;
import com.app.poke.domain.model.User;

/**
 * Logs a user in by username or email. An unknown login and a wrong password fail the same way and take about the
 * same time (the hasher still runs), so neither the message nor the timing reveals which accounts exist.
 */
public final class AuthenticateUserInteractor implements AuthenticateUserUseCase {

	private final UserGateway users;
	private final PasswordHasher hasher;
	private final TokenIssuer tokens;

	public AuthenticateUserInteractor(UserGateway users, PasswordHasher hasher, TokenIssuer tokens) {
		this.users = Objects.requireNonNull(users, "users");
		this.hasher = Objects.requireNonNull(hasher, "hasher");
		this.tokens = Objects.requireNonNull(tokens, "tokens");
	}

	@Override
	public AccessToken authenticate(AuthenticateUserCommand command) {
		var user = users.findByLogin(command.login().strip());
		var hash = user.map(User::passwordHash).orElse(null);
		if (!hasher.matches(command.password(), hash) || user.isEmpty()) {
			throw new InvalidCredentialsException();
		}
		return tokens.issue(user.get());
	}
}
