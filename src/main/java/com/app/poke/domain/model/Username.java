package com.app.poke.domain.model;

import java.util.regex.Pattern;

import com.app.poke.domain.exception.InvalidUserDataException;

/** 3 to 50 of {@code A-Z a-z 0-9 _ . -}. Unique regardless of case, but stored with the casing the user chose. */
public record Username(String value) {

	private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_.-]{3,50}");

	public Username {
		if (value == null || !VALID.matcher(value).matches()) {
			throw new InvalidUserDataException("username",
					"Username must be 3 to 50 characters: letters, digits, '_', '.' or '-'");
		}
	}
}
