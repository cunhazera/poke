package com.app.poke.application.port.out;

import java.util.Objects;

import com.app.poke.domain.model.EmailAddress;
import com.app.poke.domain.model.Role;
import com.app.poke.domain.model.Username;

/** A user about to be stored: everything but the id, with the password already hashed. */
public record NewUser(Username username, EmailAddress email, String passwordHash, Role role) {

	public NewUser {
		Objects.requireNonNull(username, "username");
		Objects.requireNonNull(email, "email");
		Objects.requireNonNull(passwordHash, "passwordHash");
		Objects.requireNonNull(role, "role");
	}

	@Override
	public String toString() {
		return "NewUser[username=%s, email=%s, role=%s]".formatted(username.value(), email.value(), role);
	}
}
