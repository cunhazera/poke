package com.app.poke.application.usecase;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Helpers for the interactors' concurrent fan-out. */
final class Futures {

	private Futures() {
	}

	/** Joins, rethrowing the original exception instead of the {@link CompletionException} wrapper. */
	static <T> T join(CompletableFuture<T> future) {
		try {
			return future.join();
		} catch (CompletionException e) {
			if (e.getCause() instanceof RuntimeException cause) {
				throw cause;
			}
			throw e;
		}
	}
}
