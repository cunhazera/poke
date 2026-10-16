package com.app.poke.application.port.out;

/**
 * The per-species data a catalog provides.
 *
 * @param category the genus, e.g. "Seed Pokémon"; null when none is available in English
 * @param description the Pokédex flavor text, whitespace normalized; null when none is available in English
 * @param evolutionChainId the catalog id of the species' evolution chain (not a Pokédex number); null if it has none
 */
public record SpeciesProfile(String category, String description, Integer evolutionChainId) {
}
