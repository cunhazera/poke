package com.app.poke.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token signing. The secret has no default in application.properties: the app refuses to start without one
 * ({@code JWT_SECRET}), so a forgotten configuration can't silently fall back to a known key.
 *
 * @param secret the HS256 key, at least 32 bytes (256 bits)
 * @param validity how long an access token stays valid
 */
@ConfigurationProperties("auth.jwt")
public record JwtProperties(String secret, @DefaultValue("1h") Duration validity) {

	static final int MIN_SECRET_BYTES = 32;

	public JwtProperties {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalArgumentException(
					"auth.jwt.secret (env JWT_SECRET) must be set to at least %d bytes".formatted(MIN_SECRET_BYTES));
		}
		if (validity.isNegative() || validity.isZero()) {
			throw new IllegalArgumentException("auth.jwt.validity must be positive, was " + validity);
		}
	}

	byte[] secretBytes() {
		return secret.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public String toString() {
		return "JwtProperties[secret=***, validity=%s]".formatted(validity);
	}
}
