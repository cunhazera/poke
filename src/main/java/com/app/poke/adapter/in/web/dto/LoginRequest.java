package com.app.poke.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

/** @param login a username or an email address */
public record LoginRequest(@NotBlank String login, @NotBlank String password) {

	@Override
	public String toString() {
		return "LoginRequest[login=%s, password=***]".formatted(login);
	}
}
