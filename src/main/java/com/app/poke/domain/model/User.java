package com.app.poke.domain.model;

import java.util.Objects;

/**
 * A registered user.
 *
 * @param passwordHash an opaque hash produced by the password hasher; never the raw password, never printed
 */
public record User(Long id, Username username, EmailAddress email, String passwordHash, Role role) {

	public User {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(username, "username");
		Objects.requireNonNull(email, "email");
		Objects.requireNonNull(role, "role");
		if (passwordHash == null || passwordHash.isBlank()) {
			throw new IllegalArgumentException("passwordHash must not be blank");
		}
	}

	@Override
	public String toString() {
		return "User[id=%d, username=%s, email=%s, role=%s]".formatted(id, username.value(), email.value(), role);
	}
}
