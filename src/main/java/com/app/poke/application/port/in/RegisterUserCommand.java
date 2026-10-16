package com.app.poke.application.port.in;

import java.util.Objects;

import com.app.poke.domain.model.EmailAddress;
import com.app.poke.domain.model.RawPassword;
import com.app.poke.domain.model.Username;

public record RegisterUserCommand(Username username, EmailAddress email, RawPassword password) {

	public RegisterUserCommand {
		Objects.requireNonNull(username, "username");
		Objects.requireNonNull(email, "email");
		Objects.requireNonNull(password, "password");
	}

	/** Validates in form order, so the first invalid field is the one reported. */
	public static RegisterUserCommand of(String username, String email, String password) {
		var validUsername = new Username(username);
		var validEmail = new EmailAddress(email);
		return new RegisterUserCommand(validUsername, validEmail, new RawPassword(password));
	}
}
