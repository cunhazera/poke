package com.app.poke.adapter.out.persistence;

import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.app.poke.application.port.out.NewUser;
import com.app.poke.application.port.out.UserGateway;
import com.app.poke.domain.exception.EmailTakenException;
import com.app.poke.domain.exception.UsernameTakenException;
import com.app.poke.domain.model.EmailAddress;
import com.app.poke.domain.model.User;
import com.app.poke.domain.model.Username;

/**
 * Users in PostgreSQL. Uniqueness is enforced by the database's {@code lower()} unique indexes, not by a prior lookup,
 * so two concurrent registrations can't both succeed; the losing insert's violation becomes a domain exception.
 */
@Component
public class JpaUserGateway implements UserGateway {

	static final String USERNAME_INDEX = "uq_app_user_username";
	static final String EMAIL_INDEX = "uq_app_user_email";

	private final UserJpaRepository repository;

	JpaUserGateway(UserJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public User create(NewUser user) {
		var entity = new UserJpaEntity(user.username().value(), user.email().value(), user.passwordHash(), user.role());
		try {
			return toDomain(repository.saveAndFlush(entity));
		} catch (DataIntegrityViolationException e) {
			throw translate(e, user);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<User> findByLogin(String login) {
		return repository.findByLogin(login).map(JpaUserGateway::toDomain);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<User> findById(long id) {
		return repository.findById(id).map(JpaUserGateway::toDomain);
	}

	private static RuntimeException translate(DataIntegrityViolationException e, NewUser user) {
		var constraint = e.getCause() instanceof ConstraintViolationException violation ? violation.getConstraintName() : null;
		if (USERNAME_INDEX.equals(constraint)) {
			return new UsernameTakenException(user.username().value());
		}
		if (EMAIL_INDEX.equals(constraint)) {
			return new EmailTakenException();
		}
		return e;
	}

	private static User toDomain(UserJpaEntity entity) {
		return new User(entity.id(), new Username(entity.username()), new EmailAddress(entity.email()),
				entity.passwordHash(), entity.role());
	}
}
