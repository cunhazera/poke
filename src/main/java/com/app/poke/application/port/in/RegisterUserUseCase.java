package com.app.poke.application.port.in;

import com.app.poke.domain.model.User;

/** Self-registration: creates a plain {@code USER}. */
public interface RegisterUserUseCase {

	User register(RegisterUserCommand command);
}
