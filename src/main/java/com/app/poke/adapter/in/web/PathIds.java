package com.app.poke.adapter.in.web;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Strict parsing of numeric path variables. They're bound as text so every malformed value ("+1", "1e3", "0x1F",
 * overflow) gets the same 400 as a non-positive one, instead of Spring's generic type-mismatch problem.
 */
final class PathIds {

	private static final Pattern DIGITS = Pattern.compile("[0-9]{1,18}");

	private PathIds() {
	}

	/** Plain decimal digits that fit in an int, or empty. */
	static Optional<Integer> asInt(String value) {
		return asLong(value).filter(n -> n <= Integer.MAX_VALUE).map(Long::intValue);
	}

	/** Plain decimal digits that fit in a long, or empty. Zero is returned as is: positivity is the caller's rule. */
	static Optional<Long> asLong(String value) {
		if (!DIGITS.matcher(value).matches()) {
			return Optional.empty();
		}
		return Optional.of(Long.parseLong(value)); // 18 digits always fit
	}
}
