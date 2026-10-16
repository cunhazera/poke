package com.app.poke.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PokemonJpaRepository extends JpaRepository<PokemonJpaEntity, Long> {

	Optional<PokemonJpaEntity> findByPokedexNumber(int pokedexNumber);

	/** A bulk delete: the database cascades to the child tables, so nothing is loaded first. */
	@Modifying
	@Query("delete from PokemonJpaEntity p where p.id = :id")
	int deleteByIdReturningCount(@Param("id") long id);
}
