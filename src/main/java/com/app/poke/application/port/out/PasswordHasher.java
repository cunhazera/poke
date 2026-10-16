package com.app.poke.application.port.out;

import com.app.poke.domain.model.RawPassword;

/** One-way password hashing. */
public interface PasswordHasher {

	String hash(RawPassword password);

	/**
	 * Whether {@code rawPassword} produces {@code hash}. {@code hash} is null when the user doesn't exist: implementations
	 * must then still spend about as long as a real check and return false, so response time doesn't reveal accounts.
	 */
	boolean matches(String rawPassword, String hash);
}
