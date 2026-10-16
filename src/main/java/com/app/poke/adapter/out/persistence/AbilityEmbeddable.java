package com.app.poke.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One {@code pokemon_ability} row. */
@Embeddable
class AbilityEmbeddable {

	@Column(nullable = false)
	private short slot;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false)
	private boolean hidden;

	protected AbilityEmbeddable() {
	}

	AbilityEmbeddable(int slot, String name, boolean hidden) {
		this.slot = (short) slot;
		this.name = name;
		this.hidden = hidden;
	}

	String name() {
		return name;
	}

	boolean hidden() {
		return hidden;
	}
}
