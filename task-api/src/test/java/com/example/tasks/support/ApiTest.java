package com.example.tasks.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.json.JsonMapper;

/**
 * Integration tests: the whole application over HTTP (MockMvc) on a real PostgreSQL migrated by Flyway. Each test
 * starts with no tasks (the five default users stay) and the clock at {@link #NOW}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiTest {

	public static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
	protected static final Instant TOMORROW = NOW.plus(Duration.ofDays(1));
	protected static final long ADA = 1, ALAN = 2, GRACE = 3;

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcTemplate db;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected JsonMapper json;

	@BeforeEach
	void cleanSlate() {
		db.execute("TRUNCATE task RESTART IDENTITY CASCADE");
		clock.set(NOW);
	}

	protected String json(Object value) {
		return json.writeValueAsString(value);
	}

	protected static Map<String, Object> task(String title, String description, Object dueDate) {
		var body = new LinkedHashMap<String, Object>();
		body.put("title", title);
		body.put("description", description);
		body.put("dueDate", dueDate == null ? null : dueDate.toString());
		return body;
	}

	protected ResultActions postJson(String path, Object body) throws Exception {
		return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json(body)));
	}

	protected ResultActions putJson(String path, Object body) throws Exception {
		return mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json(body)));
	}

	/** Creates a task through the API and returns its id. */
	protected long createTask(String title, Instant dueDate) throws Exception {
		var response = postJson("/api/tasks", task(title, null, dueDate)).andExpect(status().isCreated()).andReturn();
		return json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
	}

	protected void assign(long taskId, long userId) throws Exception {
		putJson("/api/tasks/" + taskId + "/owner", Map.of("userId", userId)).andExpect(status().isOk());
	}

	protected void move(long taskId, String status) throws Exception {
		putJson("/api/tasks/" + taskId + "/status", Map.of("status", status)).andExpect(status().isOk());
	}
}
