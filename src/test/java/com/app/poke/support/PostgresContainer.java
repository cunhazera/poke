package com.app.poke.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** The database image used by every test: the same PostgreSQL major version as compose.yaml and production. */
public final class PostgresContainer {

	public static final String IMAGE = "postgres:18-alpine";

	private PostgresContainer() {
	}

	public static PostgreSQLContainer create() {
		return new PostgreSQLContainer(IMAGE);
	}
}
