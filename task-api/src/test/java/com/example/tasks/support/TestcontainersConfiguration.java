package com.example.tasks.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A real PostgreSQL (same major version as compose.yaml) as the DataSource, and a clock tests can move. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:18-alpine");
	}

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock(ApiTest.NOW);
	}
}
