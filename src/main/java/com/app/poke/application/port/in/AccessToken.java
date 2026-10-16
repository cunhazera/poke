package com.app.poke.application.port.in;

import java.time.Duration;
import java.util.Objects;

/** A bearer token and how long it stays valid. */
public record AccessToken(String value, Duration validity) {

	public AccessToken {
		Objects.requireNonNull(value, "value");
		Objects.requireNonNull(validity, "validity");
	}

	@Override
	public String toString() {
		return "AccessToken[***, validity=%s]".formatted(validity);
	}
}
