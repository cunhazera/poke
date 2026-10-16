package com.app.poke.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.repository.query.Param;

interface UserJpaRepository extends JpaRepository<UserJpaEntity, Long> {

	/**
	 * {@code lower(col) = lower(:login)} is exactly the expression of the unique indexes (V2), so each side of the OR is an
	 * index scan. Spring Data's {@code IgnoreCase} would render {@code upper(...)} and scan the whole table.
	 */
	String FIND_BY_LOGIN = "SELECT * FROM app_user WHERE lower(username) = lower(:login) OR lower(email) = lower(:login)";

	@NativeQuery(FIND_BY_LOGIN)
	Optional<UserJpaEntity> findByLogin(@Param("login") String login);
}
