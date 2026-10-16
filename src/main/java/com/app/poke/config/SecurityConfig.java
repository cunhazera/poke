package com.app.poke.config;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.app.poke.adapter.in.web.ProblemDetailSecurityHandler;
import com.app.poke.adapter.out.security.JwtTokenIssuer;

import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless bearer-token security. Public: reading the catalog, registering, logging in, health. Everything else needs
 * a valid token, so new endpoints are protected by default.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	private static final String[] PUBLIC_READS = { "/api/v1/pokemon", "/api/v1/pokemon/**" };
	private static final String[] PUBLIC_AUTH = { "/api/v1/auth/register", "/api/v1/auth/login" };

	/**
	 * Entry point (401), access-denied handler (403) and, being the context's {@code RequestRejectedHandler}, the
	 * firewall's rejection handler (400): every security error is a ProblemDetail.
	 */
	@Bean
	ProblemDetailSecurityHandler problemDetailSecurityHandler(JsonMapper json) {
		return new ProblemDetailSecurityHandler(json);
	}

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, ProblemDetailSecurityHandler problems) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable) // no cookies or sessions: bearer tokens aren't sent automatically
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, PUBLIC_READS).permitAll()
						.requestMatchers(HttpMethod.HEAD, PUBLIC_READS).permitAll()
						.requestMatchers(HttpMethod.POST, PUBLIC_AUTH).permitAll()
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers("/error").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesFromClaim()))
						.authenticationEntryPoint(problems)
						.accessDeniedHandler(problems))
				.exceptionHandling(errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
				.build();
	}

	@Bean
	JwtEncoder jwtEncoder(JwtProperties properties) {
		return NimbusJwtEncoder.withSecretKey(key(properties)).build();
	}

	/** Checks the signature, the expiry (with Spring's default 60 s clock skew) and that we issued the token. */
	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties) {
		var decoder = NimbusJwtDecoder.withSecretKey(key(properties)).macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(JwtTokenIssuer.ISSUER));
		return decoder;
	}

	/** {@code roles: ["ADMIN"]} becomes the authority {@code ROLE_ADMIN}, for {@code hasRole("ADMIN")} rules. */
	private static JwtAuthenticationConverter rolesFromClaim() {
		var authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(JwtTokenIssuer.ROLES_CLAIM);
		authorities.setAuthorityPrefix("ROLE_");
		var converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	private static SecretKey key(JwtProperties properties) {
		return new SecretKeySpec(properties.secretBytes(), "HmacSHA256");
	}
}
