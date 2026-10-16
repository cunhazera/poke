package com.app.poke.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface PokemonJpaRepository extends JpaRepository<PokemonJpaEntity, Long> {

	Optional<PokemonJpaEntity> findByPokedexNumber(int pokedexNumber);
}
