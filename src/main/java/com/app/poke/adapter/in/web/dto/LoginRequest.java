package com.app.poke.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;

/**
 * Unknown fields are rejected API-wide, but this body keeps ignoring them, like registration.
 *
 * @param login a username or an email address
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginRequest(@NotBlank String login, @NotBlank String password) {

	@Override
	public String toString() {
		return "LoginRequest[login=%s, password=***]".formatted(login);
	}
}
