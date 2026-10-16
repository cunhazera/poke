package com.app.poke.domain.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.app.poke.domain.exception.InvalidPokemonDataException;

/**
 * Our own data about a Pokémon, which PokeAPI doesn't have and a re-sync never overwrites (US03).
 *
 * @param localizedNames names by BCP 47 language tag, e.g. {@code pt-BR → Bulbassauro}; sorted by language
 * @param region where it is found (geographical metadata); optional
 * @param habitat its environment (geographical metadata); optional
 * @param coordinates a reference sighting point; optional
 * @param tags internal classification tags, lowercase slugs such as {@code starter}; sorted
 */
public record ProprietaryData(SortedMap<String, String> localizedNames, String region, String habitat,
		Coordinates coordinates, SortedSet<String> tags) {

	public static final ProprietaryData EMPTY = new ProprietaryData(Map.of(), null, null, null, Set.of());

	private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*");
	private static final Pattern TAG = Pattern.compile("[a-z0-9][a-z0-9-]{0,49}");
	private static final int MAX_NAME = 100;
	private static final int MAX_PLACE = 50;
	public static final int MAX_LOCALIZED_NAMES = 20;
	public static final int MAX_TAGS = 20;

	public ProprietaryData(Map<String, String> localizedNames, String region, String habitat, Coordinates coordinates,
			Set<String> tags) {
		this(sorted(localizedNames), region, habitat, coordinates, sorted(tags));
	}

	public ProprietaryData {
		Objects.requireNonNull(localizedNames, "localizedNames");
		Objects.requireNonNull(tags, "tags");
		if (localizedNames.size() > MAX_LOCALIZED_NAMES) {
			throw new InvalidPokemonDataException("localizedNames", "at most %d localized names".formatted(MAX_LOCALIZED_NAMES));
		}
		if (tags.size() > MAX_TAGS) {
			throw new InvalidPokemonDataException("tags", "at most %d tags".formatted(MAX_TAGS));
		}
		localizedNames.forEach((language, name) -> {
			if (language == null || !LANGUAGE.matcher(language).matches()) {
				throw new InvalidPokemonDataException("localizedNames",
						"'%s' is not a language tag such as 'ja' or 'pt-BR'".formatted(language));
			}
			if (name == null || name.isBlank() || name.length() > MAX_NAME) {
				throw new InvalidPokemonDataException("localizedNames",
						"The %s name must be 1 to %d characters".formatted(language, MAX_NAME));
			}
		});
		tags.forEach(tag -> {
			if (tag == null || !TAG.matcher(tag).matches()) {
				throw new InvalidPokemonDataException("tags",
						"'%s' is not a tag: use lowercase letters, digits and '-', at most 50".formatted(tag));
			}
		});
		requirePlace("region", region);
		requirePlace("habitat", habitat);
		localizedNames = Collections.unmodifiableSortedMap(new TreeMap<>(localizedNames));
		tags = Collections.unmodifiableSortedSet(new TreeSet<>(tags));
	}

	private static void requirePlace(String field, String value) {
		if (value != null && (value.isBlank() || value.length() > MAX_PLACE)) {
			throw new InvalidPokemonDataException(field, "%s must be 1 to %d characters".formatted(field, MAX_PLACE));
		}
	}

	private static SortedMap<String, String> sorted(Map<String, String> map) {
		return map == null ? null : new TreeMap<>(map);
	}

	private static SortedSet<String> sorted(Set<String> set) {
		return set == null ? null : new TreeSet<>(set);
	}
}
