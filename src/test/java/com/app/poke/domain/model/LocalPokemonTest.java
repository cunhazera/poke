package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.app.poke.domain.exception.CatalogFieldsNotEditableException;
import com.app.poke.domain.exception.InvalidPokemonDataException;

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

	@Test
	void aPokemonCreatedLocallyHasNoPokedexNumberNoSyncAndNoIdYet() {
		var created = LocalPokemon.createdLocally(entry("homebrewmon", "made here"), ENRICHED);

		assertThat(created.id()).isNull();
		assertThat(created.pokedexNumber()).isNull();
		assertThat(created.syncedAt()).isNull();
		assertThat(created.version()).isZero();
		assertThat(created.proprietary()).isEqualTo(ENRICHED);
		assertThat(created.isSynced()).isFalse();
	}

	@Test
	void editingASyncedPokemonReplacesOnlyTheProprietaryData() {
		var stored = new LocalPokemon(42L, 1, entry("bulbasaur", "catalog text"), ProprietaryData.EMPTY, FIRST_SYNC, 3);

		var edited = stored.edited(null, ENRICHED, 3);

		assertThat(edited.proprietary()).isEqualTo(ENRICHED);
		assertThat(edited.entry()).isEqualTo(stored.entry());
		assertThat(edited.syncedAt()).isEqualTo(FIRST_SYNC);
		assertThat(edited.pokedexNumber()).isEqualTo(1);
		assertThat(edited.id()).isEqualTo(42L);
	}

	@Test
	void theCatalogPartOfASyncedPokemonBelongsToTheCatalog() {
		var stored = new LocalPokemon(42L, 1, entry("bulbasaur", "catalog text"), ProprietaryData.EMPTY, FIRST_SYNC, 3);

		assertThatThrownBy(() -> stored.edited(entry("bulbasaur", "my own text"), ENRICHED, 3))
				.isInstanceOf(CatalogFieldsNotEditableException.class)
				.hasMessageContaining("re-sync");
	}

	@Test
	void editingALocalPokemonReplacesEverything() {
		var stored = new LocalPokemon(7L, null, entry("homebrewmon", "draft"), ProprietaryData.EMPTY, null, 1);

		var edited = stored.edited(entry("homebrewmon", "final text"), ENRICHED, 1);

		assertThat(edited.entry().description()).isEqualTo("final text");
		assertThat(edited.proprietary()).isEqualTo(ENRICHED);
		assertThat(edited.syncedAt()).isNull();
		assertThat(edited.pokedexNumber()).isNull();
	}

	@Test
	void aLocalPokemonIsAlwaysReplacedWhole() {
		var stored = new LocalPokemon(7L, null, entry("homebrewmon", "draft"), ProprietaryData.EMPTY, null, 1);

		assertThatThrownBy(() -> stored.edited(null, ENRICHED, 1))
				.isInstanceOfSatisfying(InvalidPokemonDataException.class, e -> assertThat(e.field()).isEqualTo("name"));
	}

	@Test
	void anEditCarriesTheVersionTheEditorRead() {
		var stored = new LocalPokemon(42L, 1, entry("bulbasaur", "text"), ProprietaryData.EMPTY, FIRST_SYNC, 5);

		assertThat(stored.edited(null, ENRICHED, 4).version())
				.as("the editor's version, so the store rejects an edit made on stale data").isEqualTo(4);
	}

	@Test
	void onlyAStoredPokemonCanBeEdited() {
		var unsaved = LocalPokemon.createdLocally(entry("homebrewmon", "x"), ProprietaryData.EMPTY);

		assertThatThrownBy(() -> unsaved.edited(entry("homebrewmon", "y"), ENRICHED, 0))
				.isInstanceOf(IllegalStateException.class);
	}
}
