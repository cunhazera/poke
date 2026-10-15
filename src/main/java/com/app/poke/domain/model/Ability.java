package com.app.poke.domain.model;

/** A Pokémon skill; hidden abilities are only obtainable through special means. */
public record Ability(String name, boolean hidden) {

	public Ability {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("ability name must not be blank");
		}
	}
}
