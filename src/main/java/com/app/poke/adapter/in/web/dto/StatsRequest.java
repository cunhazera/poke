package com.app.poke.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/** The six base stats; the total is derived, so it isn't accepted. Ranges are a domain rule (BaseStats). */
public record StatsRequest(@NotNull Integer hp, @NotNull Integer attack, @NotNull Integer defense,
		@NotNull Integer specialAttack, @NotNull Integer specialDefense, @NotNull Integer speed) {
}
