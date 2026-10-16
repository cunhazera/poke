package com.app.poke.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/** Shape only (fields present); the content rules live in the domain (Username, EmailAddress, RawPassword). */
public record RegisterRequest(@NotNull String username, @NotNull String email, @NotNull String password) {

	@Override
	public String toString() {
		return "RegisterRequest[username=%s, email=%s, password=***]".formatted(username, email);
	}
}
