package com.app.poke.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.app.poke.application.exception.ConcurrentUpdateException;
import com.app.poke.application.port.in.CreateLocalPokemonCommand;
import com.app.poke.application.port.in.UpdateLocalPokemonCommand;
import com.app.poke.domain.exception.CatalogFieldsNotEditableException;
import com.app.poke.domain.exception.LocalPokemonNotFoundException;
import com.app.poke.domain.exception.PokemonNameTakenException;
import com.app.poke.domain.model.LocalPokemon;
import com.app.poke.domain.model.ProprietaryData;

/** US04: the three write use cases over the local store (create, update, delete). */
class LocalPokemonEditingTest {

	private static final Instant SYNCED_AT = Instant.parse("2026-10-01T10:00:00Z");
	private static final ProprietaryData ENRICHED = new ProprietaryData(Map.of("pt-BR", "Bulbassauro"), "Kanto",
			"grassland", null, Set.of("starter"));

	private final InMemoryLocalPokemonStore store = new InMemoryLocalPokemonStore();

	private LocalPokemon synced(int number, String name) {
		return store.insert(new LocalPokemon(null, number, LocalPokemonFixtures.entry(name, "catalog text"),
				ProprietaryData.EMPTY, SYNCED_AT, 0));
	}

	private LocalPokemon localOnly(String name) {
		return store.insert(LocalPokemon.createdLocally(LocalPokemonFixtures.entry(name, "draft"), ProprietaryData.EMPTY));
	}

	@Nested
	class Create {

		private final CreateLocalPokemonInteractor create = new CreateLocalPokemonInteractor(store);

		@Test
		void storesAPokemonThatExistsOnlyLocally() {
			var created = create.create(new CreateLocalPokemonCommand(LocalPokemonFixtures.entry("homebrewmon", "made here"),
					ENRICHED));

			assertThat(created.id()).isNotNull();
			assertThat(created.pokedexNumber()).isNull();
			assertThat(created.syncedAt()).isNull();
			assertThat(created.proprietary()).isEqualTo(ENRICHED);
			assertThat(store.findById(created.id())).contains(created);
		}

		@Test
		void aNameAlreadyInTheStoreIsTaken() {
			synced(1, "bulbasaur");

			assertThatThrownBy(() -> create.create(new CreateLocalPokemonCommand(
					LocalPokemonFixtures.entry("bulbasaur", "a fake"), ProprietaryData.EMPTY)))
					.isInstanceOf(PokemonNameTakenException.class);
			assertThat(store.rows).hasSize(1);
		}
	}

	@Nested
	class Update {

		private final UpdateLocalPokemonInteractor update = new UpdateLocalPokemonInteractor(store);

		@Test
		void aSyncedPokemonGetsNewProprietaryDataAndKeepsItsCatalogData() {
			var stored = synced(1, "bulbasaur");

			var updated = update.update(new UpdateLocalPokemonCommand(stored.id(), 0, null, ENRICHED));

			assertThat(updated.proprietary()).isEqualTo(ENRICHED);
			assertThat(updated.entry()).isEqualTo(stored.entry());
			assertThat(updated.syncedAt()).isEqualTo(SYNCED_AT);
			assertThat(updated.version()).isEqualTo(1);
			assertThat(store.findById(stored.id())).contains(updated);
		}

		@Test
		void aSyncedPokemonsCatalogDataCannotBeChangedAndNothingIsSaved() {
			var stored = synced(1, "bulbasaur");

			assertThatThrownBy(() -> update.update(new UpdateLocalPokemonCommand(stored.id(), 0,
					LocalPokemonFixtures.entry("bulbasaur", "my text"), ENRICHED)))
					.isInstanceOf(CatalogFieldsNotEditableException.class);
			assertThat(store.saves).isZero();
			assertThat(store.findById(stored.id())).contains(stored);
		}

		@Test
		void aLocalPokemonIsReplacedWhole() {
			var stored = localOnly("homebrewmon");

			var updated = update.update(new UpdateLocalPokemonCommand(stored.id(), 0,
					LocalPokemonFixtures.entry("homebrewmon-x", "final"), ENRICHED));

			assertThat(updated.entry().name()).isEqualTo("homebrewmon-x");
			assertThat(updated.entry().description()).isEqualTo("final");
			assertThat(updated.proprietary()).isEqualTo(ENRICHED);
			assertThat(updated.pokedexNumber()).isNull();
		}

		@Test
		void anEditMadeOnStaleDataIsAConflictAndChangesNothing() {
			var stored = synced(1, "bulbasaur");
			var newer = update.update(new UpdateLocalPokemonCommand(stored.id(), 0, null, ENRICHED));

			assertThatThrownBy(() -> update.update(new UpdateLocalPokemonCommand(stored.id(), 0, null, ProprietaryData.EMPTY)))
					.isInstanceOf(ConcurrentUpdateException.class);
			assertThat(store.findById(stored.id())).contains(newer);
		}

		@Test
		void renamingOntoAnotherPokemonsNameIsTaken() {
			synced(1, "bulbasaur");
			var stored = localOnly("homebrewmon");

			assertThatThrownBy(() -> update.update(new UpdateLocalPokemonCommand(stored.id(), 0,
					LocalPokemonFixtures.entry("bulbasaur", "x"), ProprietaryData.EMPTY)))
					.isInstanceOf(PokemonNameTakenException.class);
		}

		@Test
		void aMissingPokemonIsNotFound() {
			assertThatThrownBy(() -> update.update(new UpdateLocalPokemonCommand(404, 0, null, ENRICHED)))
					.isInstanceOf(LocalPokemonNotFoundException.class);
			assertThat(store.saves).isZero();
		}
	}

	@Nested
	class Delete {

		private final DeleteLocalPokemonInteractor delete = new DeleteLocalPokemonInteractor(store);

		@Test
		void removesOnlyThatPokemon() {
			var bulbasaur = synced(1, "bulbasaur");
			var homebrewmon = localOnly("homebrewmon");

			delete.delete(bulbasaur.id());

			assertThat(store.findById(bulbasaur.id())).isEmpty();
			assertThat(store.findById(homebrewmon.id())).contains(homebrewmon);
		}

		@Test
		void aMissingPokemonIsNotFound() {
			var stored = synced(1, "bulbasaur");
			delete.delete(stored.id());

			assertThatThrownBy(() -> delete.delete(stored.id())).isInstanceOf(LocalPokemonNotFoundException.class);
		}
	}
}
