package com.app.poke.application.usecase;

import java.util.ArrayList;
import java.util.List;

import com.app.poke.application.port.out.PasswordHasher;
import com.app.poke.domain.model.RawPassword;

/** A reversible stand-in for BCrypt that records every check, including checks against no hash. */
class FakePasswordHasher implements PasswordHasher {

	final List<String> checkedAgainst = new ArrayList<>();

	@Override
	public String hash(RawPassword password) {
		return "hashed(" + password.value() + ")";
	}

	@Override
	public boolean matches(String rawPassword, String hash) {
		checkedAgainst.add(String.valueOf(hash));
		return hash != null && hash.equals("hashed(" + rawPassword + ")");
	}
}
