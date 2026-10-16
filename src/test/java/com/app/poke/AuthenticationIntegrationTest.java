package com.app.poke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.app.poke.support.TestcontainersConfiguration;

import tools.jackson.databind.json.JsonMapper;

/** Registration, login and protected routes end to end: real BCrypt, real signed JWTs, real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthenticationIntegrationTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate db;

	@Autowired
	private JwtEncoder appEncoder;

	@Autowired
	private JsonMapper json;

	@BeforeEach
	void noUsers() {
		db.execute("TRUNCATE app_user RESTART IDENTITY");
	}

	private void register(String username, String email, String password) throws Exception {
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
				{ "username": "%s", "email": "%s", "password": "%s" }""".formatted(username, email, password)))
				.andExpect(status().isCreated());
	}

	private String login(String login, String password) throws Exception {
		var body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
				{ "login": "%s", "password": "%s" }""".formatted(login, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(body).get("accessToken").asString();
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	/** A token like the app's, with the given expiry and issuer, signed by {@code encoder}. */
	private static String token(JwtEncoder encoder, Instant expiresAt, String issuer) {
		var claims = JwtClaimsSet.builder().issuer(issuer).subject("1").issuedAt(expiresAt.minusSeconds(3600))
				.expiresAt(expiresAt).claim("roles", List.of("USER")).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	@Test
	void registerThenLoginThenCallAProtectedRoute() throws Exception {
		register("AshKetchum", "Ash@Pallet.town", "pikachu123");

		var token = login("ASH@PALLET.TOWN", "pikachu123");

		mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("AshKetchum"))
				.andExpect(jsonPath("$.email").value("Ash@Pallet.town"))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void thePasswordIsStoredAsABcryptHash() throws Exception {
		register("ash", "ash@pallet.town", "pikachu123");

		assertThat(db.queryForObject("SELECT password_hash FROM app_user", String.class))
				.startsWith("$2a$10$").doesNotContain("pikachu123");
	}

	@Test
	void usernameAndEmailAreUniqueRegardlessOfCase() throws Exception {
		register("AshKetchum", "Ash@Pallet.town", "pikachu123");

		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"username\": \"ashketchum\", \"email\": \"new@pallet.town\", \"password\": \"pikachu123\" }"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Username taken"));
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"username\": \"misty\", \"email\": \"ASH@pallet.town\", \"password\": \"starmie123\" }"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.title").value("Email already registered"));
	}

	@Test
	void wrongPasswordAndUnknownUserGetTheSameAnswer() throws Exception {
		register("ash", "ash@pallet.town", "pikachu123");

		var wrongPassword = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"login\": \"ash\", \"password\": \"pikachu124\" }"))
				.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
		var unknownUser = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"login\": \"gary\", \"password\": \"pikachu124\" }"))
				.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

		assertThat(json.readTree(unknownUser).get("detail")).isEqualTo(json.readTree(wrongPassword).get("detail"));
	}

	@Test
	void protectedRoutesRejectMissingMalformedExpiredForgedAndForeignTokens() throws Exception {
		var otherKey = new SecretKeySpec("someone-elses-secret-also-32-bytes-long".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		var forged = token(NimbusJwtEncoder.withSecretKey(otherKey).build(), Instant.now().plusSeconds(600), "poke");
		var expired = token(appEncoder, Instant.now().minusSeconds(120), "poke"); // past the 60 s clock skew
		var foreignIssuer = token(appEncoder, Instant.now().plusSeconds(600), "someone-else");

		mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
		for (var bad : List.of("not-a-jwt", forged, expired, foreignIssuer)) {
			mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(bad)))
					.andExpect(status().isUnauthorized())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Authentication required"));
		}
	}

	@Test
	void theCatalogAndHealthArePublic() throws Exception {
		mvc.perform(get("/api/v1/pokemon/0")).andExpect(status().isBadRequest()); // reached the controller, no token
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void everythingElseIsProtectedByDefault() throws Exception {
		register("ash", "ash@pallet.town", "pikachu123");
		var token = login("ash", "pikachu123");

		mvc.perform(get("/api/v1/not-a-route")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/not-a-route").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isNotFound());
		mvc.perform(post("/api/v1/pokemon/1")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/pokemon/1").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isMethodNotAllowed());
		mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
	}
}
