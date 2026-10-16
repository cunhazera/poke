package com.app.poke.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.poke.application.exception.ConcurrentUpdateException;
import com.app.poke.application.port.out.LocalPokemonGateway;
import com.app.poke.domain.exception.PokemonNameTakenException;
import com.app.poke.domain.model.Ability;
import com.app.poke.domain.model.BaseStats;
import com.app.poke.domain.model.Coordinates;
import com.app.poke.domain.model.Height;
import com.app.poke.domain.model.LocalPokemon;
import com.app.poke.domain.model.NameQuery;
import com.app.poke.domain.model.Page;
import com.app.poke.domain.model.PageRequest;
import com.app.poke.domain.model.PokedexEntry;
import com.app.poke.domain.model.ProprietaryData;
import com.app.poke.domain.model.Weight;

/**
 * Local Pokémon in PostgreSQL. Optimistic locking: a save carries the version it was read at, and a stale one is a
 * {@link ConcurrentUpdateException} (409), never a silent lost update.
 */
@Component
public class JpaLocalPokemonGateway implements LocalPokemonGateway {

	static final String POKEDEX_NUMBER_KEY = "pokemon_pokedex_number_key";
	static final String NAME_KEY = "uq_pokemon_name";
	private static final Sort POKEDEX_ORDER = Sort.by(Sort.Order.asc("pokedexNumber").nullsLast(), Sort.Order.asc("id"));

	private final PokemonJpaRepository repository;

	JpaLocalPokemonGateway(PokemonJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public LocalPokemon save(LocalPokemon pokemon) {
		var entity = pokemon.id() == null ? new PokemonJpaEntity(pokemon.pokedexNumber()) : loadForUpdate(pokemon);
		copyInto(entity, pokemon);
		try {
			return toDomain(repository.saveAndFlush(entity));
		} catch (OptimisticLockingFailureException e) {
			throw new ConcurrentUpdateException("Local Pokémon %d changed concurrently".formatted(pokemon.id()));
		} catch (DataIntegrityViolationException e) {
			if (POKEDEX_NUMBER_KEY.equals(constraintOf(e))) {
				throw new ConcurrentUpdateException("Pokémon #%d was stored concurrently".formatted(pokemon.pokedexNumber()));
			}
			if (NAME_KEY.equals(constraintOf(e))) {
				throw new PokemonNameTakenException(pokemon.entry().name());
			}
			throw e;
		}
	}

	/** One statement; the child rows go with it through the schema's {@code ON DELETE CASCADE}. */
	@Override
	@Transactional
	public boolean deleteById(long id) {
		return repository.deleteByIdReturningCount(id) > 0;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<LocalPokemon> findById(long id) {
		return repository.findById(id).map(JpaLocalPokemonGateway::toDomain);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<LocalPokemon> findByPokedexNumber(int pokedexNumber) {
		return repository.findByPokedexNumber(pokedexNumber).map(JpaLocalPokemonGateway::toDomain);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<LocalPokemon> findAll(PageRequest request) {
		// Spring Data's PageRequest, not the domain one of the same name.
		var page = repository.findAll(org.springframework.data.domain.PageRequest.of(request.page(), request.size(), POKEDEX_ORDER));
		return new Page<>(page.getContent().stream().map(JpaLocalPokemonGateway::toDomain).toList(), request,
				page.getTotalElements());
	}

	@Override
	@Transactional(readOnly = true)
	public Page<LocalPokemon> search(NameQuery query, PageRequest request) {
		var page = repository.search(containing(query.slug()), containing(query.text()),
				org.springframework.data.domain.PageRequest.of(request.page(), request.size(), POKEDEX_ORDER));
		return new Page<>(page.getContent().stream().map(JpaLocalPokemonGateway::toDomain).toList(), request,
				page.getTotalElements());
	}

	/** A LIKE pattern for "contains", with the user's own {@code %} and {@code _} matched literally. */
	static String containing(String text) {
		return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
	}

	/** The copy being saved must still be the stored one; otherwise someone else changed it after we read it. */
	private PokemonJpaEntity loadForUpdate(LocalPokemon pokemon) {
		var entity = repository.findById(pokemon.id())
				.orElseThrow(() -> new ConcurrentUpdateException("Local Pokémon %d was deleted".formatted(pokemon.id())));
		if (entity.version() != pokemon.version()) {
			throw new ConcurrentUpdateException("Local Pokémon %d changed concurrently".formatted(pokemon.id()));
		}
		return entity;
	}

	private static void copyInto(PokemonJpaEntity entity, LocalPokemon pokemon) {
		var e = pokemon.entry();
		var s = e.stats();
		entity.setCatalogData(e.name(), e.category(), e.description(), e.height().decimetres(), e.weight().hectograms(),
				e.spriteUrl(), e.imageUrl(),
				new short[] { (short) s.hp(), (short) s.attack(), (short) s.defense(), (short) s.specialAttack(),
						(short) s.specialDefense(), (short) s.speed() },
				slotted(e.types(), (slot, type) -> new TypeEmbeddable(slot, type)),
				slotted(e.abilities(), (slot, a) -> new AbilityEmbeddable(slot, a.name(), a.hidden())));
		var p = pokemon.proprietary();
		var at = p.coordinates();
		entity.setProprietaryData(p.localizedNames(), p.region(), p.habitat(), at == null ? null : at.latitude(),
				at == null ? null : at.longitude(), p.tags());
		entity.setSyncedAt(pokemon.syncedAt());
	}

	private static LocalPokemon toDomain(PokemonJpaEntity entity) {
		var s = entity.stats();
		var entry = new PokedexEntry(entity.name(), entity.category(), entity.description(),
				Height.ofDecimetres(entity.heightDm()), Weight.ofHectograms(entity.weightHg()), entity.spriteUrl(),
				entity.imageUrl(), entity.types().stream().map(TypeEmbeddable::name).toList(),
				entity.abilities().stream().map(a -> new Ability(a.name(), a.hidden())).toList(),
				new BaseStats(s[0], s[1], s[2], s[3], s[4], s[5]));
		var coordinates = entity.latitude() == null ? null : new Coordinates(entity.latitude(), entity.longitude());
		var proprietary = new ProprietaryData(entity.localizedNames(), entity.region(), entity.habitat(), coordinates,
				entity.tags());
		return new LocalPokemon(entity.id(), entity.pokedexNumber(), entry, proprietary, entity.syncedAt(), entity.version());
	}

	/** Slots are positions in the domain's ordered list, starting at 1 like PokeAPI's and the schema's. */
	private static <T, R> List<R> slotted(List<T> items, BiFunction<Integer, T, R> toRow) {
		return IntStream.range(0, items.size()).mapToObj(i -> toRow.apply(i + 1, items.get(i))).toList();
	}

	private static String constraintOf(DataIntegrityViolationException e) {
		return e.getCause() instanceof ConstraintViolationException violation ? violation.getConstraintName() : null;
	}
}
