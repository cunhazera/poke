package com.app.poke.application.port.out;

/**
 * The per-species data a catalog provides.
 *
 * @param category the genus, e.g. "Seed Pokémon"; null when none is available in English
 */
public record SpeciesProfile(String category) {
}
