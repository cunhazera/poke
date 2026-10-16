package com.app.poke.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PokemonJpaRepository extends JpaRepository<PokemonJpaEntity, Long> {

	Optional<PokemonJpaEntity> findByPokedexNumber(int pokedexNumber);

	/**
	 * The name contains {@code slug}, or a localized name contains {@code text}, both lowercase LIKE patterns escaped
	 * with {@code !}. {@code EXISTS} rather than a join, so a Pokémon with several matching names is one row and the
	 * page and its count stay right.
	 */
	@Query(value = SEARCH, countQuery = "select count(p) " + SEARCH_WHERE)
	Page<PokemonJpaEntity> search(@Param("slug") String slugPattern, @Param("text") String textPattern, Pageable pageable);

	String SEARCH_WHERE = """
			from PokemonJpaEntity p
			where lower(p.name) like :slug escape '!'
			   or exists (select 1 from PokemonJpaEntity o join o.localizedNames n
			              where o = p and lower(n) like :text escape '!')
			""";
	String SEARCH = "select p " + SEARCH_WHERE;

	/** A bulk delete: the database cascades to the child tables, so nothing is loaded first. */
	@Modifying
	@Query("delete from PokemonJpaEntity p where p.id = :id")
	int deleteByIdReturningCount(@Param("id") long id);
}
