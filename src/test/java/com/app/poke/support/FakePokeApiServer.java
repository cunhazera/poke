package com.app.poke.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A real HTTP server standing in for PokeAPI, so adapter and end-to-end tests exercise the actual HTTP
 * client, JSON parsing, timeouts and status handling. Unstubbed paths answer like PokeAPI does: a JSON 404.
 * Fixtures under {@code src/test/resources/pokeapi} may use {@code {{base}}} as a placeholder for {@link #baseUrl()}.
 */
public final class FakePokeApiServer implements AutoCloseable {

	private record Stub(int status, String body, Duration delay) {
	}

	private final HttpServer server;
	/** The one call that lists every species (the catalog index). */
	public static final String SPECIES_INDEX = "/pokemon-species?limit=100000";

	private final Map<String, Stub> stubs = new ConcurrentHashMap<>();
	private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

	private FakePokeApiServer() {
		try {
			server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.createContext("/", this::handle);
		server.start();
	}

	public static FakePokeApiServer start() {
		return new FakePokeApiServer();
	}

	public String baseUrl() {
		return "http://localhost:" + server.getAddress().getPort() + "/api/v2";
	}

	/** @param pathAndQuery relative to the base URL, e.g. {@code /pokemon/1} or {@link #SPECIES_INDEX} */
	public FakePokeApiServer stub(String pathAndQuery, int status, String body) {
		return stub(pathAndQuery, status, body, Duration.ZERO);
	}

	public FakePokeApiServer stub(String pathAndQuery, int status, String body, Duration delay) {
		stubs.put("/api/v2" + pathAndQuery, new Stub(status, body.replace("{{base}}", baseUrl()), delay));
		return this;
	}

	public FakePokeApiServer stubFixture(String pathAndQuery, String fixture) {
		return stub(pathAndQuery, 200, fixture(fixture));
	}

	/** Stubs the species list plus pokemon and species detail for ids 1-3 (bulbasaur, ivysaur, venusaur). */
	public FakePokeApiServer stubFirstThree() {
		stubFixture(SPECIES_INDEX, "species-index.json");
		return stubBulbasaurLine();
	}

	/** Stubs pokemon and species 1-3 and their evolution chain (id 1): everything a detail view of any of them needs. */
	public FakePokeApiServer stubBulbasaurLine() {
		for (int id = 1; id <= 3; id++) {
			stubFixture("/pokemon/" + id, "pokemon-" + id + ".json");
			stubFixture("/pokemon-species/" + id, "species-" + id + ".json");
		}
		return stubFixture("/evolution-chain/1", "evolution-chain-1.json");
	}

	public int hits(String pathAndQuery) {
		var counter = hits.get("/api/v2" + pathAndQuery);
		return counter == null ? 0 : counter.get();
	}

	public int totalHits() {
		return hits.values().stream().mapToInt(AtomicInteger::get).sum();
	}

	public static String fixture(String name) {
		try (var in = FakePokeApiServer.class.getResourceAsStream("/pokeapi/" + name)) {
			if (in == null) {
				throw new IllegalArgumentException("missing fixture " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void handle(HttpExchange exchange) throws IOException {
		var uri = exchange.getRequestURI();
		var key = uri.getPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
		hits.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
		var stub = stubs.getOrDefault(key, new Stub(404, "{\"status\":404,\"message\":\"Not Found\"}", Duration.ZERO));
		try {
			Thread.sleep(stub.delay());
			var bytes = stub.body().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
			exchange.sendResponseHeaders(stub.status(), bytes.length);
			exchange.getResponseBody().write(bytes);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			// client gave up (e.g. read timeout) before the response was written
		} finally {
			exchange.close();
		}
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
