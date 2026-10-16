package com.app.poke.application.port.out;

import java.util.Optional;

import com.app.poke.domain.exception.EmailTakenException;
import com.app.poke.domain.exception.UsernameTakenException;
import com.app.poke.domain.model.User;

/** Where users are stored. Usernames and emails are unique regardless of case, and lookups ignore case too. */
public interface UserGateway {

	/**
	 * @throws UsernameTakenException if the username exists in any case
	 * @throws EmailTakenException if the email exists in any case
	 */
	User create(NewUser user);

	/** The user whose username or email equals {@code login}, ignoring case. */
	Optional<User> findByLogin(String login);

	Optional<User> findById(long id);
}
