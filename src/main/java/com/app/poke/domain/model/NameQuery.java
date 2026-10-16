package com.app.poke.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

import com.app.poke.domain.exception.InvalidSearchException;

/**
 * What a user typed to find Pokémon by name. Names are lowercase slugs ({@code mr-mime}, {@code farfetchd}), so the
 * text is also offered in that spelling: {@code "Mr. Mime"} finds {@code mr-mime}.
 *
 * @param text what was typed, trimmed and lowercased (for free text such as localized names)
 */
public record NameQuery(String text) {

	public static final int MAX_LENGTH = 50;

	private static final Pattern EDGE_SEPARATORS = Pattern.compile("^[\\s.]+|[\\s.]+$");
	private static final Pattern SEPARATORS = Pattern.compile("[\\s.]+");
	private static final Pattern REPEATED_HYPHENS = Pattern.compile("-{2,}");

	public NameQuery {
		if (text == null) {
			throw new InvalidSearchException("Search text is required");
		}
		text = text.strip().toLowerCase(Locale.ROOT);
		if (text.length() > MAX_LENGTH) {
			throw new InvalidSearchException("Search text must be at most %d characters".formatted(MAX_LENGTH));
		}
		if (text.codePoints().noneMatch(Character::isLetterOrDigit)) {
			throw new InvalidSearchException("Search text must contain a letter or a digit");
		}
	}

	/** The text spelled like a PokeAPI name: no apostrophes, spaces and dots become hyphens. */
	public String slug() {
		var withoutApostrophes = text.replace("'", "").replace("’", "");
		var hyphenated = SEPARATORS.matcher(EDGE_SEPARATORS.matcher(withoutApostrophes).replaceAll("")).replaceAll("-");
		return REPEATED_HYPHENS.matcher(hyphenated).replaceAll("-");
	}

	/** Whether a slug name ({@code charmander}) contains this search, ignoring case. */
	public boolean matchesSlug(String name) {
		return name.toLowerCase(Locale.ROOT).contains(slug());
	}
}
