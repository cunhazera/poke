package com.app.poke.adapter.in.web.dto;

/**
 * @param tokenType always "Bearer": send it as {@code Authorization: Bearer <accessToken>}
 * @param expiresIn seconds until the token expires
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

	public static TokenResponse bearer(String accessToken, long expiresIn) {
		return new TokenResponse(accessToken, "Bearer", expiresIn);
	}
}
