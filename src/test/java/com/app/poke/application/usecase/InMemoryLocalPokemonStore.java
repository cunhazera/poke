package com.app.poke.application.usecase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.app.poke.application.exception.ConcurrentUpdateException;
import com.app.poke.application.port.out.LocalPokemonGateway;
import com.app.poke.domain.model.LocalPokemon;
import com.app.poke.domain.model.Page;
import com.app.poke.domain.model.PageRequest;

/** Behaves like the database: assigns ids, bumps the version on every save, rejects stale versions and duplicates. */
class InMemoryLocalPokemonStore implements LocalPokemonGateway {

	final List<LocalPokemon> rows = new ArrayList<>();
	int saves;
	private Runnable beforeSave = () -> { };

	/** Simulates another request committing between our read and our save. */
	InMemoryLocalPokemonStore beforeSave(Runnable hook) {
		this.beforeSave = hook;
		return this;
	}

	LocalPokemon insert(LocalPokemon pokemon) {
		var stored = new LocalPokemon((long) rows.size() + 1, pokemon.pokedexNumber(), pokemon.entry(),
				pokemon.proprietary(), pokemon.syncedAt(), 0);
		rows.add(stored);
		return stored;
	}

	@Override
	public LocalPokemon save(LocalPokemon pokemon) {
		beforeSave.run();
		saves++;
		if (pokemon.id() == null) {
			if (pokemon.pokedexNumber() != null && findByPokedexNumber(pokemon.pokedexNumber()).isPresent()) {
				throw new ConcurrentUpdateException("Pokémon #" + pokemon.pokedexNumber() + " was stored concurrently");
			}
			return insert(pokemon);
		}
		var index = indexOf(pokemon.id());
		if (rows.get(index).version() != pokemon.version()) {
			throw new ConcurrentUpdateException("Local Pokémon " + pokemon.id() + " changed concurrently");
		}
		var updated = new LocalPokemon(pokemon.id(), pokemon.pokedexNumber(), pokemon.entry(), pokemon.proprietary(),
				pokemon.syncedAt(), pokemon.version() + 1);
		rows.set(index, updated);
		return updated;
	}

	@Override
	public Optional<LocalPokemon> findById(long id) {
		return rows.stream().filter(p -> p.id() == id).findFirst();
	}

	@Override
	public Optional<LocalPokemon> findByPokedexNumber(int pokedexNumber) {
		return rows.stream().filter(p -> Objects.equals(p.pokedexNumber(), pokedexNumber)).findFirst();
	}

	@Override
	public Page<LocalPokemon> findAll(PageRequest request) {
		var ordered = rows.stream()
				.sorted(Comparator.comparing(LocalPokemon::pokedexNumber, Comparator.nullsLast(Comparator.naturalOrder()))
						.thenComparing(LocalPokemon::id))
				.toList();
		var items = ordered.stream().skip(request.offset()).limit(request.size()).toList();
		return new Page<>(items, request, ordered.size());
	}

	private int indexOf(long id) {
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).id() == id) {
				return i;
			}
		}
		throw new IllegalStateException("no row " + id);
	}
}
