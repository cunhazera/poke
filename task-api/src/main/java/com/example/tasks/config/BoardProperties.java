package com.example.tasks.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param adminUsername the board admin, told about every overdue task (a default user: {@code ada}) */
@ConfigurationProperties(prefix = "tasks.board")
public record BoardProperties(String adminUsername) {

	public BoardProperties {
		if (adminUsername == null || adminUsername.isBlank()) {
			adminUsername = "ada";
		}
	}
}
