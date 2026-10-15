package com.app.poke.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pokeapi")
public record PokeApiProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
