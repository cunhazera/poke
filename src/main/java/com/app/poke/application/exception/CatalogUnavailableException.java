package com.app.poke.application.exception;

/** The external Pokémon catalog failed, timed out, or returned unusable data. */
public class CatalogUnavailableException extends RuntimeException {

	public CatalogUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
