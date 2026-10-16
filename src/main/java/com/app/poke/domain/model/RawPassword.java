package com.app.poke.domain.model;

import java.nio.charset.StandardCharsets;

import com.app.poke.domain.exception.InvalidUserDataException;

/**
 * A password as typed, before hashing: 8 to 72 bytes. The upper bound is in UTF-8 bytes because BCrypt only reads the
 * first 72. Never printed: {@link #toString()} is masked and error messages don't echo it.
 */
public record RawPassword(String value) {

	public static final int MIN_BYTES = 8;
	public static final int MAX_BYTES = 72;

	public RawPassword {
		var bytes = value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
		if (bytes < MIN_BYTES || bytes > MAX_BYTES) {
			throw new InvalidUserDataException("password",
					"Password must be %d to %d bytes long".formatted(MIN_BYTES, MAX_BYTES));
		}
	}

	@Override
	public String toString() {
		return "RawPassword[***]";
	}
}
