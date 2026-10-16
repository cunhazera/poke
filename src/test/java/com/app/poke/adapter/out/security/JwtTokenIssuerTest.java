package com.app.poke.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.app.poke.domain.model.EmailAddress;
import com.app.poke.domain.model.Role;
import com.app.poke.domain.model.User;
import com.app.poke.domain.model.Username;

class JwtTokenIssuerTest {

	private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
	private static final SecretKeySpec KEY = key("test-secret-that-is-at-least-32-bytes!!");
	private static final User OAK = new User(7L, new Username("oak"), new EmailAddress("oak@pallet.town"), "h", Role.ADMIN);

	private final JwtTokenIssuer issuer = new JwtTokenIssuer(NimbusJwtEncoder.withSecretKey(KEY).build(),
			Duration.ofMinutes(30), Clock.fixed(NOW, ZoneOffset.UTC));

	private static SecretKeySpec key(String secret) {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	private static NimbusJwtDecoder decoderFor(SecretKeySpec key) {
		var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success()); // signature only: expiry is checked by the app's decoder
		return decoder;
	}

	@Test
	void carriesIdentityRolesAndExpiry() {
		var token = issuer.issue(OAK);

		var jwt = decoderFor(KEY).decode(token.value());
		assertThat(jwt.getSubject()).isEqualTo("7");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("poke");
		assertThat(jwt.getClaimAsString("username")).isEqualTo("oak");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
		assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
		assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
		assertThat(token.validity()).isEqualTo(Duration.ofMinutes(30));
	}

	@Test
	void isSignedWithHs256AndCarriesOnlyTheseClaims() {
		var jwt = decoderFor(KEY).decode(issuer.issue(OAK).value());

		assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
		// JWT payloads are only base64-encoded, so nothing private (email, hash) may be a claim.
		assertThat(jwt.getClaims()).containsOnlyKeys("iss", "sub", "iat", "exp", "username", "roles");
	}

	@Test
	void aTokenSignedWithAnotherKeyIsRejected() {
		var token = issuer.issue(OAK).value();

		assertThatThrownBy(() -> decoderFor(key("a-different-secret-also-32-bytes-long!!")).decode(token))
				.isInstanceOf(JwtException.class);
	}
}
