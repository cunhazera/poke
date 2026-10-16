package com.app.poke.application.port.out;

import com.app.poke.application.port.in.AccessToken;
import com.app.poke.domain.model.User;

/** Issues the credential an authenticated user presents on later requests. */
public interface TokenIssuer {

	AccessToken issue(User user);
}
