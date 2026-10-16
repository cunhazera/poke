package com.app.poke.application.port.in;

import com.app.poke.domain.model.User;

/** The user an authenticated request belongs to. */
public interface GetCurrentUserUseCase {

	/** @throws com.app.poke.domain.exception.UserNotFoundException if the user no longer exists */
	User get(long userId);
}
