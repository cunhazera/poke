package com.app.poke.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import com.app.poke.adapter.out.security.BCryptPasswordHasher;
import com.app.poke.adapter.out.security.JwtTokenIssuer;
import com.app.poke.application.port.in.AuthenticateUserUseCase;
import com.app.poke.application.port.in.GetCurrentUserUseCase;
import com.app.poke.application.port.in.RegisterUserUseCase;
import com.app.poke.application.port.out.PasswordHasher;
import com.app.poke.application.port.out.TokenIssuer;
import com.app.poke.application.port.out.UserGateway;
import com.app.poke.application.usecase.AuthenticateUserInteractor;
import com.app.poke.application.usecase.GetCurrentUserInteractor;
import com.app.poke.application.usecase.RegisterUserInteractor;

/** Composition root for users and authentication. The user gateway is the JPA adapter (a component). */
@Configuration
class AuthConfig {

	@Bean
	PasswordHasher passwordHasher() {
		return new BCryptPasswordHasher();
	}

	@Bean
	TokenIssuer tokenIssuer(JwtEncoder encoder, JwtProperties properties) {
		return new JwtTokenIssuer(encoder, properties.validity(), Clock.systemUTC());
	}

	@Bean
	RegisterUserUseCase registerUserUseCase(UserGateway users, PasswordHasher hasher) {
		return new RegisterUserInteractor(users, hasher);
	}

	@Bean
	AuthenticateUserUseCase authenticateUserUseCase(UserGateway users, PasswordHasher hasher, TokenIssuer tokens) {
		return new AuthenticateUserInteractor(users, hasher, tokens);
	}

	@Bean
	GetCurrentUserUseCase getCurrentUserUseCase(UserGateway users) {
		return new GetCurrentUserInteractor(users);
	}
}
