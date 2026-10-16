package com.app.poke.application.port.in;

import java.util.Objects;

/**
 * Login credentials as typed. The password is not checked against the registration policy, only against the stored
 * hash.
 *
 * @param login a username or an email address
 */
public record AuthenticateUserCommand(String login, String password) {

	public AuthenticateUserCommand {
		Objects.requireNonNull(login, "login");
		Objects.requireNonNull(password, "password");
	}

	@Override
	public String toString() {
		return "AuthenticateUserCommand[login=%s, password=***]".formatted(login);
	}
}
