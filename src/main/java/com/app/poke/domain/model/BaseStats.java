package com.app.poke.domain.model;

import com.app.poke.domain.exception.InvalidPokemonDataException;

/** The six base stats. Each is within 1..255, the range of every Pokémon game. */
public record BaseStats(int hp, int attack, int defense, int specialAttack, int specialDefense, int speed) {

	public static final int MIN = 1;
	public static final int MAX = 255;

	public BaseStats {
		requireInRange("hp", hp);
		requireInRange("attack", attack);
		requireInRange("defense", defense);
		requireInRange("specialAttack", specialAttack);
		requireInRange("specialDefense", specialDefense);
		requireInRange("speed", speed);
	}

	/** The base stat total, derived so it can never disagree with the stats. */
	public int total() {
		return hp + attack + defense + specialAttack + specialDefense + speed;
	}

	private static void requireInRange(String stat, int value) {
		if (value < MIN || value > MAX) {
			throw new InvalidPokemonDataException("stats", "%s must be within %d..%d, was %d".formatted(stat, MIN, MAX, value));
		}
	}
}
