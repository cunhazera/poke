package com.app.poke.domain.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.app.poke.domain.exception.InvalidPokemonDataException;

/**
 * The catalog part of a local Pokémon: copied from PokeAPI and refreshed by every sync, or written by hand for a Pokémon
 * created locally (US04). The same rules hold for both, and they match what PokeAPI's data looks like.
 *
 * @param name a lowercase slug, as PokeAPI names Pokémon ({@code mr-mime})
 * @param category the species genus; may be null
 * @param description the Pokédex flavor text; may be null
 * @param spriteUrl an absolute http(s) URL; may be null
 * @param imageUrl an absolute http(s) URL; may be null
 * @param types one or two distinct canonical types, in slot order
 * @param abilities at most three, with distinct names, in slot order
 */
public record PokedexEntry(String name, String category, String description, Height height, Weight weight,
		String spriteUrl, String imageUrl, List<String> types, List<Ability> abilities, BaseStats stats) {

	public static final int MAX_ABILITIES = 3;
	public static final int MAX_NAME = 100;
	public static final int MAX_CATEGORY = 100;
	public static final int MAX_DESCRIPTION = 2000;
	public static final int MAX_URL = 500;
	public static final int MAX_ABILITY_NAME = 50;

	private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]*");

	public PokedexEntry {
		if (name == null || name.length() > MAX_NAME || !SLUG.matcher(name).matches()) {
			throw new InvalidPokemonDataException("name",
					"name must be 1 to %d lowercase letters, digits or '-', not starting with '-', was '%s'".formatted(MAX_NAME, name));
		}
		requireText("category", category, MAX_CATEGORY);
		requireText("description", description, MAX_DESCRIPTION);
		Objects.requireNonNull(height, "height");
		Objects.requireNonNull(weight, "weight");
		requireUrl("spriteUrl", spriteUrl);
		requireUrl("imageUrl", imageUrl);
		Objects.requireNonNull(stats, "stats");
		types = List.copyOf(Objects.requireNonNull(types, "types"));
		if (types.isEmpty() || types.size() > 2 || types.size() != new HashSet<>(types).size()) {
			throw new InvalidPokemonDataException("types", "a Pokémon has one or two distinct types, was " + types);
		}
		types.stream().filter(type -> !PokemonType.isCanonical(type)).findFirst().ifPresent(type -> {
			throw new InvalidPokemonDataException("types", "'%s' is not a type such as 'fire' or 'water'".formatted(type));
		});
		abilities = List.copyOf(Objects.requireNonNull(abilities, "abilities"));
		if (abilities.size() > MAX_ABILITIES) {
			throw new InvalidPokemonDataException("abilities",
					"at most %d abilities, was %d".formatted(MAX_ABILITIES, abilities.size()));
		}
		var names = new HashSet<String>();
		for (var ability : abilities) {
			if (ability.name().length() > MAX_ABILITY_NAME || !SLUG.matcher(ability.name()).matches()) {
				throw new InvalidPokemonDataException("abilities", "'%s' is not an ability name: use 1 to %d lowercase letters, digits or '-'"
						.formatted(ability.name(), MAX_ABILITY_NAME));
			}
			if (!names.add(ability.name())) {
				throw new InvalidPokemonDataException("abilities", "'%s' is listed twice".formatted(ability.name()));
			}
		}
	}

	private static void requireText(String field, String value, int max) {
		if (value != null && (value.isBlank() || value.length() > max)) {
			throw new InvalidPokemonDataException(field, "%s must be 1 to %d characters".formatted(field, max));
		}
	}

	private static void requireUrl(String field, String value) {
		if (value != null && (value.length() > MAX_URL || !isHttpUrl(value))) {
			throw new InvalidPokemonDataException(field,
					"%s must be an absolute http(s) URL of at most %d characters".formatted(field, MAX_URL));
		}
	}

	private static boolean isHttpUrl(String value) {
		try {
			var uri = new URI(value);
			return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null;
		} catch (URISyntaxException e) {
			return false;
		}
	}
}
