package com.app.poke.adapter.in.web.dto;

/** The six base stats, plus their total. */
public record BaseStatsResponse(int hp, int attack, int defense, int specialAttack, int specialDefense, int speed,
		int total) {
}
