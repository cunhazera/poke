package com.app.poke.application.port.in;

/** Login: exchanges valid credentials for an access token. */
public interface AuthenticateUserUseCase {

	/** @throws com.app.poke.application.exception.InvalidCredentialsException for an unknown login or a wrong password alike */
	AccessToken authenticate(AuthenticateUserCommand command);
}
