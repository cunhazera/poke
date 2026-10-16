package com.app.poke.adapter.out.security;

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.app.poke.application.port.out.PasswordHasher;
import com.app.poke.domain.model.RawPassword;

/** BCrypt with a per-password random salt. Checks against a missing hash still run BCrypt (on a dummy hash). */
public class BCryptPasswordHasher implements PasswordHasher {

	public static final int STRENGTH = 10;

	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(STRENGTH);
	private final String dummyHash = encoder.encode("dummy password used to equalize timing");

	@Override
	public String hash(RawPassword password) {
		return encoder.encode(password.value());
	}

	@Override
	public boolean matches(String rawPassword, String hash) {
		if (hash == null) {
			encoder.matches(rawPassword, dummyHash);
			return false;
		}
		// BCrypt reads only 72 bytes and Spring rejects longer input; such a password can't match any stored hash.
		return rawPassword.getBytes(StandardCharsets.UTF_8).length <= RawPassword.MAX_BYTES
				&& encoder.matches(rawPassword, hash);
	}
}
