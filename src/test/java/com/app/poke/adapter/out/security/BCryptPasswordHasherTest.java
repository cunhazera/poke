package com.app.poke.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import com.app.poke.domain.model.RawPassword;

class BCryptPasswordHasherTest {

	private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

	@Test
	void producesASaltedBcryptHashThatVerifies() {
		var hash = hasher.hash(new RawPassword("pikachu123"));

		assertThat(hash).startsWith("$2a$10$").doesNotContain("pikachu123");
		assertThat(hasher.matches("pikachu123", hash)).isTrue();
		assertThat(hasher.matches("pikachu124", hash)).isFalse();
		assertThat(hasher.matches("PIKACHU123", hash)).isFalse();
	}

	@Test
	void theSamePasswordHashesDifferentlyEachTime() {
		assertThat(hasher.hash(new RawPassword("pikachu123"))).isNotEqualTo(hasher.hash(new RawPassword("pikachu123")));
	}

	@Test
	void aMissingHashNeverMatches() {
		assertThat(hasher.matches("pikachu123", null)).isFalse();
	}

	@Test
	void aMissingHashCostsAboutAsMuchAsARealCheck() {
		var hash = hasher.hash(new RawPassword("pikachu123"));
		hasher.matches("warm-up", hash);

		var real = time(() -> hasher.matches("wrong-password", hash));
		var missing = time(() -> hasher.matches("wrong-password", null));

		// BCrypt at strength 10 takes tens of milliseconds; skipping it would take microseconds.
		assertThat(missing).isGreaterThan(real.dividedBy(4));
	}

	@Test
	void anOverlongLoginPasswordIsJustAMismatchNotAnError() {
		var hash = hasher.hash(new RawPassword("p".repeat(72)));

		assertThat(hasher.matches("p".repeat(73), hash)).isFalse();
	}

	private static Duration time(BooleanSupplier check) {
		var start = System.nanoTime();
		check.getAsBoolean();
		return Duration.ofNanos(System.nanoTime() - start);
	}
}
