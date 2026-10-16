package com.app.poke.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.app.poke.application.exception.InvalidCredentialsException;
import com.app.poke.application.port.in.AccessToken;
import com.app.poke.application.port.in.AuthenticateUserCommand;
import com.app.poke.application.port.in.AuthenticateUserUseCase;
import com.app.poke.application.port.in.GetCurrentUserUseCase;
import com.app.poke.application.port.in.RegisterUserCommand;
import com.app.poke.application.port.in.RegisterUserUseCase;
import com.app.poke.config.SecurityConfig;
import com.app.poke.domain.exception.EmailTakenException;
import com.app.poke.domain.exception.UserNotFoundException;
import com.app.poke.domain.exception.UsernameTakenException;
import com.app.poke.domain.model.EmailAddress;
import com.app.poke.domain.model.Role;
import com.app.poke.domain.model.User;
import com.app.poke.domain.model.Username;

/** Web slice for the auth API with the real security rules; the use cases are stubbed. */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

	private static final User ASH = new User(7L, new Username("AshKetchum"), new EmailAddress("Ash@Pallet.town"),
			"$2a$10$secret-hash", Role.USER);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private RegisterUserUseCase registerUser;

	@MockitoBean
	private AuthenticateUserUseCase authenticateUser;

	@MockitoBean
	private GetCurrentUserUseCase currentUser;

	private static MockHttpServletRequestBuilder postJson(String path, String json) {
		return post(path).contentType(MediaType.APPLICATION_JSON).content(json);
	}

	@Nested
	class Register {

		private static final String ASH_JSON = """
				{ "username": "AshKetchum", "email": "Ash@Pallet.town", "password": "pikachu123" }""";

		@Test
		void createsAUserWithoutAToken() throws Exception {
			given(registerUser.register(any())).willReturn(ASH);

			mvc.perform(postJson("/api/v1/auth/register", ASH_JSON))
					.andExpect(status().isCreated())
					.andExpect(content().json("""
							{ "id": 7, "username": "AshKetchum", "email": "Ash@Pallet.town", "role": "USER" }
							""", JsonCompareMode.STRICT))
					.andExpect(content().string(not(containsString("secret-hash"))))
					.andExpect(content().string(not(containsString("pikachu123"))));

			verify(registerUser).register(RegisterUserCommand.of("AshKetchum", "Ash@Pallet.town", "pikachu123"));
		}

		@Test
		void anUnknownRoleFieldIsIgnoredNotRejected() throws Exception {
			given(registerUser.register(any())).willReturn(ASH);

			mvc.perform(postJson("/api/v1/auth/register", ASH_JSON.replace("{", "{ \"role\": \"ADMIN\",")))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.role").value("USER"));

			verify(registerUser).register(RegisterUserCommand.of("AshKetchum", "Ash@Pallet.town", "pikachu123"));
		}

		@Test
		void domainRuleViolationNamesTheField() throws Exception {
			mvc.perform(postJson("/api/v1/auth/register", ASH_JSON.replace("pikachu123", "short")))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Invalid user data"))
					.andExpect(jsonPath("$.field").value("password"))
					.andExpect(content().string(not(containsString("short\""))));

			verifyNoInteractions(registerUser);
		}

		@Test
		void missingFieldsAreListed() throws Exception {
			mvc.perform(postJson("/api/v1/auth/register", "{ \"username\": \"ash\" }"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").value("Invalid request body"))
					.andExpect(jsonPath("$.errors.email").exists())
					.andExpect(jsonPath("$.errors.password").exists())
					.andExpect(jsonPath("$.errors.username").doesNotExist());

			verifyNoInteractions(registerUser);
		}

		@ParameterizedTest
		@ValueSource(strings = { "{ not json", "", "[]" })
		void malformedBodyIsBadRequest(String body) throws Exception {
			mvc.perform(postJson("/api/v1/auth/register", body))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

			verifyNoInteractions(registerUser);
		}

		@Test
		void onlyJsonIsAccepted() throws Exception {
			mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.content("username=ash&email=a@b.io&password=pikachu123"))
					.andExpect(status().isUnsupportedMediaType());
		}

		@Test
		void aTakenUsernameIsAConflict() throws Exception {
			given(registerUser.register(any())).willThrow(new UsernameTakenException("AshKetchum"));

			mvc.perform(postJson("/api/v1/auth/register", ASH_JSON))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.title").value("Username taken"));
		}

		@Test
		void aTakenEmailIsAConflict() throws Exception {
			given(registerUser.register(any())).willThrow(new EmailTakenException());

			mvc.perform(postJson("/api/v1/auth/register", ASH_JSON))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.title").value("Email already registered"));
		}
	}

	@Nested
	class Login {

		@Test
		void returnsABearerTokenWithoutNeedingOne() throws Exception {
			given(authenticateUser.authenticate(new AuthenticateUserCommand("ash", "pikachu123")))
					.willReturn(new AccessToken("signed.jwt.value", Duration.ofHours(1)));

			mvc.perform(postJson("/api/v1/auth/login", "{ \"login\": \"ash\", \"password\": \"pikachu123\" }"))
					.andExpect(status().isOk())
					.andExpect(content().json("""
							{ "accessToken": "signed.jwt.value", "tokenType": "Bearer", "expiresIn": 3600 }
							""", JsonCompareMode.STRICT));
		}

		@Test
		void badCredentialsAreUnauthorizedWithAChallenge() throws Exception {
			given(authenticateUser.authenticate(any())).willThrow(new InvalidCredentialsException());

			mvc.perform(postJson("/api/v1/auth/login", "{ \"login\": \"ash\", \"password\": \"nope-nope\" }"))
					.andExpect(status().isUnauthorized())
					.andExpect(header().string("WWW-Authenticate", "Bearer"))
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Invalid credentials"));
		}

		@Test
		void blankCredentialsAreRejectedBeforeAnyLookup() throws Exception {
			mvc.perform(postJson("/api/v1/auth/login", "{ \"login\": \" \", \"password\": \"\" }"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.login").exists())
					.andExpect(jsonPath("$.errors.password").exists());

			verifyNoInteractions(authenticateUser);
		}
	}

	@Nested
	class Me {

		@Test
		void returnsTheUserNamedByTheTokenSubject() throws Exception {
			given(currentUser.get(7)).willReturn(ASH);

			mvc.perform(get("/api/v1/auth/me").with(jwt().jwt(token -> token.subject("7"))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.username").value("AshKetchum"))
					.andExpect(jsonPath("$.passwordHash").doesNotExist());
		}

		@Test
		void withoutATokenIsUnauthorizedAsAProblemDetail() throws Exception {
			mvc.perform(get("/api/v1/auth/me"))
					.andExpect(status().isUnauthorized())
					.andExpect(header().string("WWW-Authenticate", "Bearer"))
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Authentication required"))
					.andExpect(jsonPath("$.instance").value("/api/v1/auth/me"));

			verifyNoInteractions(currentUser);
		}

		@Test
		void aTokenForADeletedUserIsNotFound() throws Exception {
			given(currentUser.get(7)).willThrow(new UserNotFoundException(7));

			mvc.perform(get("/api/v1/auth/me").with(jwt().jwt(token -> token.subject("7"))))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.title").value("User not found"));
		}
	}
}
