package com.app.poke.adapter.out.security;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.app.poke.application.port.in.AccessToken;
import com.app.poke.application.port.out.TokenIssuer;
import com.app.poke.domain.model.User;

/**
 * Signed JWTs (HS256): {@code sub} is the user id, {@code roles} drives authorization. Stateless, so no server-side
 * session; a token stays valid until it expires.
 */
public class JwtTokenIssuer implements TokenIssuer {

	public static final String ISSUER = "poke";
	public static final String ROLES_CLAIM = "roles";
	public static final String USERNAME_CLAIM = "username";

	private final JwtEncoder encoder;
	private final Duration validity;
	private final Clock clock;

	public JwtTokenIssuer(JwtEncoder encoder, Duration validity, Clock clock) {
		this.encoder = encoder;
		this.validity = validity;
		this.clock = clock;
	}

	@Override
	public AccessToken issue(User user) {
		var now = clock.instant();
		var claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(String.valueOf(user.id()))
				.issuedAt(now)
				.expiresAt(now.plus(validity))
				.claim(USERNAME_CLAIM, user.username().value())
				.claim(ROLES_CLAIM, List.of(user.role().name()))
				.build();
		var header = JwsHeader.with(MacAlgorithm.HS256).build();
		var token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(token, validity);
	}
}
