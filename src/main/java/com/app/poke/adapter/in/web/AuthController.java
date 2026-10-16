package com.app.poke.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.app.poke.adapter.in.web.dto.LoginRequest;
import com.app.poke.adapter.in.web.dto.RegisterRequest;
import com.app.poke.adapter.in.web.dto.TokenResponse;
import com.app.poke.adapter.in.web.dto.UserResponse;
import com.app.poke.application.port.in.AuthenticateUserCommand;
import com.app.poke.application.port.in.AuthenticateUserUseCase;
import com.app.poke.application.port.in.GetCurrentUserUseCase;
import com.app.poke.application.port.in.RegisterUserCommand;
import com.app.poke.application.port.in.RegisterUserUseCase;
import com.app.poke.domain.model.User;

import jakarta.validation.Valid;

/** Registration and login are public; {@code /me} needs a bearer token (see SecurityConfig). */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final RegisterUserUseCase registerUser;
	private final AuthenticateUserUseCase authenticateUser;
	private final GetCurrentUserUseCase currentUser;

	AuthController(RegisterUserUseCase registerUser, AuthenticateUserUseCase authenticateUser,
			GetCurrentUserUseCase currentUser) {
		this.registerUser = registerUser;
		this.authenticateUser = authenticateUser;
		this.currentUser = currentUser;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse register(@Valid @RequestBody RegisterRequest request) {
		var user = registerUser.register(RegisterUserCommand.of(request.username(), request.email(), request.password()));
		return toResponse(user);
	}

	@PostMapping("/login")
	TokenResponse login(@Valid @RequestBody LoginRequest request) {
		var token = authenticateUser.authenticate(new AuthenticateUserCommand(request.login(), request.password()));
		return TokenResponse.bearer(token.value(), token.validity().toSeconds());
	}

	@GetMapping("/me")
	UserResponse me(@AuthenticationPrincipal Jwt token) {
		return toResponse(currentUser.get(Long.parseLong(token.getSubject())));
	}

	private static UserResponse toResponse(User user) {
		return new UserResponse(user.id(), user.username().value(), user.email().value(), user.role().name());
	}
}
