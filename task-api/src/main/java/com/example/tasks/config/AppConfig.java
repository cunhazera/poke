package com.example.tasks.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(BoardProperties.class)
public class AppConfig {

	/**
	 * The one source of "now" (UTC); tests replace it to move time past due dates. It ticks in microseconds, the
	 * precision of PostgreSQL's timestamptz, so a response never shows a time that reads back differently later.
	 */
	@Bean
	Clock clock() {
		return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
	}
}
