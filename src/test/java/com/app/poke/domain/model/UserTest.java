package com.app.poke.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserTest {

	private static final Username ASH = new Username("ash");
	private static final EmailAddress EMAIL = new EmailAddress("ash@pallet.town");

	@Test
	void theHashNeverAppearsInToString() {
		var user = new User(1L, ASH, EMAIL, "$2a$10$secret-hash", Role.USER);

		assertThat(user.toString()).doesNotContain("secret-hash").contains("ash");
	}

	@Test
	void requiresEveryField() {
		assertThatThrownBy(() -> new User(null, ASH, EMAIL, "h", Role.USER)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new User(1L, null, EMAIL, "h", Role.USER)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new User(1L, ASH, null, "h", Role.USER)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new User(1L, ASH, EMAIL, " ", Role.USER)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new User(1L, ASH, EMAIL, "h", null)).isInstanceOf(NullPointerException.class);
	}
}
