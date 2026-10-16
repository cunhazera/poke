package com.app.poke.config;

import java.time.Clock;
import java.util.concurrent.ExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.app.poke.application.port.in.CreateLocalPokemonUseCase;
import com.app.poke.application.port.in.DeleteLocalPokemonUseCase;
import com.app.poke.application.port.in.GetLocalPokemonUseCase;
import com.app.poke.application.port.in.ListLocalPokemonUseCase;
import com.app.poke.application.port.in.SyncPokemonUseCase;
import com.app.poke.application.port.in.UpdateLocalPokemonUseCase;
import com.app.poke.application.port.out.LocalPokemonGateway;
import com.app.poke.application.port.out.PokemonCatalogGateway;
import com.app.poke.application.usecase.CreateLocalPokemonInteractor;
import com.app.poke.application.usecase.DeleteLocalPokemonInteractor;
import com.app.poke.application.usecase.GetLocalPokemonInteractor;
import com.app.poke.application.usecase.ListLocalPokemonInteractor;
import com.app.poke.application.usecase.SyncPokemonInteractor;
import com.app.poke.application.usecase.UpdateLocalPokemonInteractor;

/** Composition root for the local store (US03, US04). The store itself is the JPA adapter (a component). */
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

	@Bean
	CreateLocalPokemonUseCase createLocalPokemonUseCase(LocalPokemonGateway store) {
		return new CreateLocalPokemonInteractor(store);
	}

	@Bean
	UpdateLocalPokemonUseCase updateLocalPokemonUseCase(LocalPokemonGateway store) {
		return new UpdateLocalPokemonInteractor(store);
	}

	@Bean
	DeleteLocalPokemonUseCase deleteLocalPokemonUseCase(LocalPokemonGateway store) {
		return new DeleteLocalPokemonInteractor(store);
	}
}
