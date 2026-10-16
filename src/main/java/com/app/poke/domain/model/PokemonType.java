package com.app.poke.domain.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** The eighteen elemental types of the games. Their API names are lowercase, as PokeAPI spells them ("fire"). */
public enum PokemonType {
	NORMAL, FIRE, WATER, GRASS, ELECTRIC, ICE, FIGHTING, POISON, GROUND, FLYING, PSYCHIC, BUG, ROCK, GHOST, DRAGON, DARK,
	STEEL, FAIRY;

	private static final Set<String> API_NAMES = Arrays.stream(values()).map(PokemonType::apiName)
			.collect(Collectors.toUnmodifiableSet());

	public String apiName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static boolean isCanonical(String name) {
		return name != null && API_NAMES.contains(name);
	}
}
