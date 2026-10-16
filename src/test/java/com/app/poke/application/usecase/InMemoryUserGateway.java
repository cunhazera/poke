package com.app.poke.application.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.app.poke.application.port.out.NewUser;
import com.app.poke.application.port.out.UserGateway;
import com.app.poke.domain.exception.EmailTakenException;
import com.app.poke.domain.exception.UsernameTakenException;
import com.app.poke.domain.model.User;

/** Behaves like the database: ids are assigned, username and email are unique regardless of case. */
class InMemoryUserGateway implements UserGateway {

	final List<User> users = new ArrayList<>();

	@Override
	public User create(NewUser user) {
		if (users.stream().anyMatch(u -> same(u.username().value(), user.username().value()))) {
			throw new UsernameTakenException(user.username().value());
		}
		if (users.stream().anyMatch(u -> same(u.email().value(), user.email().value()))) {
			throw new EmailTakenException();
		}
		var created = new User((long) users.size() + 1, user.username(), user.email(), user.passwordHash(), user.role());
		users.add(created);
		return created;
	}

	@Override
	public Optional<User> findByLogin(String login) {
		return users.stream().filter(u -> same(u.username().value(), login) || same(u.email().value(), login)).findFirst();
	}

	@Override
	public Optional<User> findById(long id) {
		return users.stream().filter(u -> u.id() == id).findFirst();
	}

	private static boolean same(String a, String b) {
		return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
	}
}
