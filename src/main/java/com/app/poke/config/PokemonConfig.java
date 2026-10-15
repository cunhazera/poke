package com.app.poke.config;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.app.poke.adapter.out.pokeapi.PokeApiCatalogGateway;
import com.app.poke.application.port.in.ListPokemonUseCase;
import com.app.poke.application.port.out.PokemonCatalogGateway;
import com.app.poke.application.usecase.ListPokemonInteractor;

/** Composition root for the Pokémon feature: binds interactors to their gateways. */
@Configuration
@EnableCaching
@EnableConfigurationProperties(PokeApiProperties.class)
class PokemonConfig {

	@Bean
	PokeApiCatalogGateway pokeApiCatalogGateway(RestClient.Builder builder, PokeApiProperties properties) {
		var httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return new PokeApiCatalogGateway(builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build());
	}

	/** Virtual threads: the fan-out is I/O-bound, so one cheap thread per upstream call. */
	@Bean(destroyMethod = "close")
	ExecutorService catalogFetchExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

	@Bean
	ListPokemonUseCase listPokemonUseCase(PokemonCatalogGateway catalog, ExecutorService catalogFetchExecutor) {
		return new ListPokemonInteractor(catalog, catalogFetchExecutor);
	}
}
