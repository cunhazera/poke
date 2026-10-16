package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LocalPokemonTest {

	private static final Instant FIRST_SYNC = Instant.parse("2026-10-01T10:00:00Z");
	private static final Instant RE_SYNC = Instant.parse("2026-10-15T10:00:00Z");

	static PokedexEntry entry(String name, String description) {
		return new PokedexEntry(name, "Seed Pokémon", description, Height.ofDecimetres(7), Weight.ofHectograms(69),
				"https://sprites/1.png", "https://art/1.png", List.of("grass", "poison"),
				List.of(new Ability("overgrow", false)), new BaseStats(45, 49, 49, 65, 65, 45));
	}

	private static final ProprietaryData ENRICHED = new ProprietaryData(Map.of("pt-BR", "Bulbassauro"), "Kanto",
			"grassland", new Coordinates(new BigDecimal("35.6762"), new BigDecimal("139.6503")), Set.of("starter"));

	@Test
	void aFirstSyncStartsWithNoProprietaryDataAndNoId() {
		var local = LocalPokemon.fromCatalog(new PokedexNumber(1), entry("bulbasaur", "old"), FIRST_SYNC);

		assertThat(local.id()).isNull();
		assertThat(local.pokedexNumber()).isEqualTo(1);
		assertThat(local.proprietary()).isEqualTo(ProprietaryData.EMPTY);
		assertThat(local.syncedAt()).isEqualTo(FIRST_SYNC);
	}

	@Test
	void aReSyncReplacesTheCatalogDataAndKeepsEverythingProprietary() {
		var stored = new LocalPokemon(42L, 1, entry("bulbasaur", "old text"), ENRICHED, FIRST_SYNC, 3);

		var refreshed = stored.refreshedFrom(entry("bulbasaur", "new text"), RE_SYNC);

		assertThat(refreshed.entry().description()).isEqualTo("new text");
		assertThat(refreshed.syncedAt()).isEqualTo(RE_SYNC);
		assertThat(refreshed.proprietary()).isEqualTo(ENRICHED);
		assertThat(refreshed.id()).isEqualTo(42L);
		assertThat(refreshed.pokedexNumber()).isEqualTo(1);
		assertThat(refreshed.version()).as("the stored version, so a concurrent change is detected on save").isEqualTo(3);
	}

	@Test
	void onlyACatalogCopyCanBeReSynced() {
		var localOnly = new LocalPokemon(7L, null, entry("homebrewmon", "made here"), ENRICHED, null, 0);

		assertThatThrownBy(() -> localOnly.refreshedFrom(entry("x", "y"), RE_SYNC)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void requiresItsParts() {
		assertThatThrownBy(() -> new LocalPokemon(1L, 1, null, ENRICHED, FIRST_SYNC, 0)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new LocalPokemon(1L, 1, entry("a", "b"), null, FIRST_SYNC, 0)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new LocalPokemon(1L, 0, entry("a", "b"), ENRICHED, FIRST_SYNC, 0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new LocalPokemon(1L, 1, entry("a", "b"), ENRICHED, FIRST_SYNC, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
