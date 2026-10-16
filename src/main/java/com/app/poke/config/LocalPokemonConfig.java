package com.app.poke.config;

import java.time.Clock;
import java.util.concurrent.ExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.app.poke.application.port.in.GetLocalPokemonUseCase;
import com.app.poke.application.port.in.ListLocalPokemonUseCase;
import com.app.poke.application.port.in.SyncPokemonUseCase;
import com.app.poke.application.port.out.LocalPokemonGateway;
import com.app.poke.application.port.out.PokemonCatalogGateway;
import com.app.poke.application.usecase.GetLocalPokemonInteractor;
import com.app.poke.application.usecase.ListLocalPokemonInteractor;
import com.app.poke.application.usecase.SyncPokemonInteractor;

/** Composition root for the local store (US03). The store itself is the JPA adapter (a component). */
@Configuration
class LocalPokemonConfig {

	@Bean
	SyncPokemonUseCase syncPokemonUseCase(PokemonCatalogGateway catalog, LocalPokemonGateway store,
			ExecutorService catalogFetchExecutor) {
		return new SyncPokemonInteractor(catalog, store, catalogFetchExecutor, Clock.systemUTC());
	}

	@Bean
	ListLocalPokemonUseCase listLocalPokemonUseCase(LocalPokemonGateway store) {
		return new ListLocalPokemonInteractor(store);
	}

	@Bean
	GetLocalPokemonUseCase getLocalPokemonUseCase(LocalPokemonGateway store) {
		return new GetLocalPokemonInteractor(store);
	}
}
