package com.app.poke.domain.model;

import java.util.regex.Pattern;

import com.app.poke.domain.exception.InvalidUserDataException;

/**
 * An email address, trimmed, at most 254 characters (the RFC 5321 path limit). The format check is a sanity check;
 * only delivering mail proves an address. Unique regardless of case, but stored as typed.
 */
public record EmailAddress(String value) {

	public static final int MAX_LENGTH = 254;
	private static final Pattern VALID = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	public EmailAddress {
		value = value == null ? null : value.strip();
		if (value == null || value.length() > MAX_LENGTH || !VALID.matcher(value).matches()) {
			throw new InvalidUserDataException("email", "Email must be a valid address of at most 254 characters");
		}
	}
}
