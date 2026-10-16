package com.app.poke.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

/**
 * Shape only (fields present); the content rules live in the domain (Username, EmailAddress, RawPassword). Unknown
 * fields are rejected API-wide, but this body keeps ignoring them, so a {@code role} is silently dropped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegisterRequest(@NotNull String username, @NotNull String email, @NotNull String password) {

	@Override
	public String toString() {
		return "RegisterRequest[username=%s, email=%s, password=***]".formatted(username, email);
	}
}
