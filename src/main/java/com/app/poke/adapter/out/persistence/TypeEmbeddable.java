package com.app.poke.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One {@code pokemon_type} row. */
@Embeddable
class TypeEmbeddable {

	@Column(nullable = false)
	private short slot;

	@Column(nullable = false, length = 30)
	private String name;

	protected TypeEmbeddable() {
	}

	TypeEmbeddable(int slot, String name) {
		this.slot = (short) slot;
		this.name = name;
	}

	String name() {
		return name;
	}
}
