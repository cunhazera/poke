package com.example.tasks.task;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.example.tasks.support.ApiTest;

/** The task CRUD over HTTP: the contract of each operation and every error it can return. */
class TaskCrudApiTest extends ApiTest {

	private static ResultActions isProblem(ResultActions actions, int status, String title) throws Exception {
		return actions.andExpect(status().is(status))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.title").value(title))
				.andExpect(content().string(not(containsString("Exception"))))
				.andExpect(content().string(not(containsString("\tat "))));
	}

	@Nested
	class Create {

		@Test
		void createsATaskInTodoWithoutAnOwner() throws Exception {
			postJson("/api/tasks", task("Write the report", "Quarterly numbers", "2026-10-20T18:00:00-03:00"))
					.andExpect(status().isCreated())
					.andExpect(header().string("Location", endsWith("/api/tasks/1")))
					.andExpect(content().json("""
							{ "id": 1, "title": "Write the report", "description": "Quarterly numbers", "status": "TODO",
							  "dueDate": "2026-10-20T21:00:00Z", "owner": null, "overdue": false, "warning": null,
							  "createdAt": "2026-10-16T12:00:00Z", "updatedAt": "2026-10-16T12:00:00Z" }
							""", org.springframework.test.json.JsonCompareMode.STRICT));
		}

		@Test
		void recordsTheCreationInTheHistory() throws Exception {
			var id = createTask("t", TOMORROW);

			mvc.perform(get("/api/tasks/" + id + "/history"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(1)))
					.andExpect(jsonPath("$[0].action").value("CREATED"))
					.andExpect(jsonPath("$[0].toStatus").value("TODO"))
					.andExpect(jsonPath("$[0].occurredAt").value("2026-10-16T12:00:00Z"));
		}

		@Test
		void theDescriptionIsOptional() throws Exception {
			postJson("/api/tasks", Map.of("title", "t", "dueDate", TOMORROW.toString()))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.description").value(nullValue()));
		}

		@Test
		void acceptsA124CharacterTitleButNot125() throws Exception {
			postJson("/api/tasks", task("x".repeat(124), null, TOMORROW)).andExpect(status().isCreated());

			isProblem(postJson("/api/tasks", task("x".repeat(125), null, TOMORROW)), 400, "Invalid task")
					.andExpect(jsonPath("$.errors.title").value("must be at most 124 characters"));
		}

		@Test
		void reportsEveryInvalidFieldAtOnce() throws Exception {
			isProblem(postJson("/api/tasks", task("  ", null, null)), 400, "Invalid task")
					.andExpect(jsonPath("$.errors.title").value("is required"))
					.andExpect(jsonPath("$.errors.dueDate").value("is required"));
		}

		@Test
		void theDueDateMustBeInTheFuture() throws Exception {
			isProblem(postJson("/api/tasks", task("t", null, NOW)), 400, "Invalid task")
					.andExpect(jsonPath("$.errors.dueDate").value("must be in the future"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "tomorrow", "2026-10-20", "2026-10-20T18:00:00", "2026-13-01T00:00:00Z" })
		void theDueDateNeedsADateTimeWithAnOffset(String dueDate) throws Exception {
			isProblem(postJson("/api/tasks", Map.of("title", "t", "dueDate", dueDate)), 400, "Invalid field value")
					.andExpect(jsonPath("$.errors.dueDate").value(containsString("ISO-8601")));
		}

		@ParameterizedTest
		@ValueSource(strings = { "status", "owner", "ownerId", "id" })
		void statusOwnerAndIdCannotBeSetOnCreation(String field) throws Exception {
			var body = new java.util.HashMap<String, Object>(task("t", null, TOMORROW));
			body.put(field, field.equals("status") ? "DONE" : 1);

			isProblem(postJson("/api/tasks", body), 400, "Unknown field")
					.andExpect(jsonPath("$.errors." + field).value("is not a field of this request"));
			mvc.perform(get("/api/tasks")).andExpect(jsonPath("$.totalItems").value(0));
		}

		@ParameterizedTest
		@ValueSource(strings = { "{", "not json", "[]", "" })
		void aMalformedBodyIsBadRequest(String body) throws Exception {
			isProblem(postJson("/api/tasks", body), 400, body.equals("[]") ? "Malformed request body" : "Malformed request body");
		}

		@Test
		void aBodyThatIsNotJsonIsUnsupported() throws Exception {
			mvc.perform(post("/api/tasks").contentType(MediaType.TEXT_PLAIN).content("title=t"))
					.andExpect(status().isUnsupportedMediaType())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
	}

	@Nested
	class Read {

		@Test
		void getsOneTask() throws Exception {
			var id = createTask("Write", TOMORROW);

			mvc.perform(get("/api/tasks/" + id))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.id").value(id))
					.andExpect(jsonPath("$.title").value("Write"));
		}

		@Test
		void anUnknownTaskIsNotFound() throws Exception {
			isProblem(mvc.perform(get("/api/tasks/999")), 404, "Not found").andExpect(jsonPath("$.detail").value("Task 999 not found"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "abc", "1.5", "99999999999999999999" })
		void aNonNumericIdIsBadRequest(String id) throws Exception {
			isProblem(mvc.perform(get("/api/tasks/" + id)), 400, "Invalid parameter")
					.andExpect(jsonPath("$.errors.id").value("must be a whole number"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "0", "-1" })
		void aNonPositiveIdIsBadRequest(String id) throws Exception {
			isProblem(mvc.perform(get("/api/tasks/" + id)), 400, "Invalid parameter")
					.andExpect(jsonPath("$.errors.id").value("must be greater than 0"));
		}
	}

	@Nested
	class List {

		@Test
		void listsByDueDateInAPage() throws Exception {
			createTask("later", TOMORROW.plus(Duration.ofDays(2)));
			createTask("sooner", TOMORROW);
			createTask("middle", TOMORROW.plus(Duration.ofDays(1)));

			mvc.perform(get("/api/tasks").param("size", "2"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items[*].title").value(org.hamcrest.Matchers.contains("sooner", "middle")))
					.andExpect(jsonPath("$.page").value(0))
					.andExpect(jsonPath("$.size").value(2))
					.andExpect(jsonPath("$.totalItems").value(3))
					.andExpect(jsonPath("$.totalPages").value(2));
			mvc.perform(get("/api/tasks").param("page", "1").param("size", "2"))
					.andExpect(jsonPath("$.items[*].title").value(org.hamcrest.Matchers.contains("later")));
		}

		@Test
		void filtersByStatusOwnerAssignmentAndOverdue() throws Exception {
			var todo = createTask("todo", TOMORROW);
			var mine = createTask("mine", TOMORROW.plusSeconds(1));
			var soonOverdue = createTask("overdue", NOW.plusSeconds(60));
			assign(mine, ADA);
			move(mine, "IN_PROGRESS");
			assign(soonOverdue, ALAN);
			clock.advance(Duration.ofMinutes(5));

			expectTitles(get("/api/tasks").param("status", "IN_PROGRESS"), "mine");
			expectTitles(get("/api/tasks").param("ownerId", String.valueOf(ADA)), "mine");
			expectTitles(get("/api/tasks").param("unassigned", "true"), "todo");
			expectTitles(get("/api/tasks").param("unassigned", "false"), "overdue", "mine");
			expectTitles(get("/api/tasks").param("overdue", "true"), "overdue");
			expectTitles(get("/api/tasks").param("overdue", "false"), "todo", "mine");
			expectTitles(get("/api/tasks").param("status", "TODO").param("ownerId", String.valueOf(ALAN)), "overdue");
			mvc.perform(get("/api/tasks/" + todo)).andExpect(jsonPath("$.overdue").value(false));
		}

		private void expectTitles(org.springframework.test.web.servlet.RequestBuilder request, String... titles) throws Exception {
			mvc.perform(request).andExpect(status().isOk())
					.andExpect(jsonPath("$.items[*].title").value(org.hamcrest.Matchers.contains(titles)));
		}

		@Test
		void anEmptyListIsAnEmptyPage() throws Exception {
			mvc.perform(get("/api/tasks"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items", hasSize(0)))
					.andExpect(jsonPath("$.totalItems").value(0))
					.andExpect(jsonPath("$.totalPages").value(0));
		}

		@Test
		void anUnknownStatusListsTheValidOnes() throws Exception {
			isProblem(mvc.perform(get("/api/tasks").param("status", "FINISHED")), 400, "Invalid parameter")
					.andExpect(jsonPath("$.errors.status").value("must be one of TODO, IN_PROGRESS, DONE"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "page=-1", "size=0", "size=101", "ownerId=0" })
		void outOfRangeParametersAreBadRequest(String query) throws Exception {
			var name = query.split("=")[0];
			isProblem(mvc.perform(get("/api/tasks?" + query)), 400, "Invalid parameter")
					.andExpect(jsonPath("$.errors." + name).exists());
		}

		@ParameterizedTest
		@ValueSource(strings = { "page=abc", "size=1.5", "ownerId=abc", "unassigned=maybe", "overdue=2" })
		void malformedParametersAreBadRequest(String query) throws Exception {
			var name = query.split("=")[0];
			isProblem(mvc.perform(get("/api/tasks?" + query)), 400, "Invalid parameter")
					.andExpect(jsonPath("$.errors." + name).exists());
		}
	}

	@Nested
	class Update {

		@Test
		void replacesTheDetailsAndRecordsIt() throws Exception {
			var id = createTask("Old", TOMORROW);
			clock.advance(Duration.ofMinutes(1));

			putJson("/api/tasks/" + id, task("New", "Now with a description", TOMORROW.plus(Duration.ofDays(1))))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.title").value("New"))
					.andExpect(jsonPath("$.description").value("Now with a description"))
					.andExpect(jsonPath("$.dueDate").value("2026-10-18T12:00:00Z"))
					.andExpect(jsonPath("$.status").value("TODO"))
					.andExpect(jsonPath("$.updatedAt").value("2026-10-16T12:01:00Z"));
			mvc.perform(get("/api/tasks/" + id + "/history"))
					.andExpect(jsonPath("$[*].action").value(org.hamcrest.Matchers.contains("CREATED", "DETAILS_UPDATED")));
		}

		@Test
		void anOmittedDescriptionIsCleared() throws Exception {
			var id = createTask("t", TOMORROW);
			putJson("/api/tasks/" + id, task("t", "text", TOMORROW)).andExpect(status().isOk());

			putJson("/api/tasks/" + id, Map.of("title", "t", "dueDate", TOMORROW.toString()))
					.andExpect(jsonPath("$.description").value(nullValue()));
		}

		@Test
		void validatesLikeCreation() throws Exception {
			var id = createTask("t", TOMORROW);

			isProblem(putJson("/api/tasks/" + id, task("", null, NOW.minusSeconds(1))), 400, "Invalid task")
					.andExpect(jsonPath("$.errors.title").exists())
					.andExpect(jsonPath("$.errors.dueDate").value("must be in the future"));
			mvc.perform(get("/api/tasks/" + id)).andExpect(jsonPath("$.title").value("t"));
		}

		@Test
		void statusCannotBeChangedThroughTheDetails() throws Exception {
			var id = createTask("t", TOMORROW);
			var body = new java.util.HashMap<String, Object>(task("t", null, TOMORROW));
			body.put("status", "DONE");

			isProblem(putJson("/api/tasks/" + id, body), 400, "Unknown field");
		}

		@Test
		void anUnknownTaskIsNotFound() throws Exception {
			isProblem(putJson("/api/tasks/999", task("t", null, TOMORROW)), 404, "Not found");
		}
	}

	@Nested
	class Delete {

		@Test
		void deletesTheTaskAndItsHistory() throws Exception {
			var id = createTask("t", TOMORROW);

			mvc.perform(delete("/api/tasks/" + id)).andExpect(status().isNoContent()).andExpect(content().string(""));

			isProblem(mvc.perform(get("/api/tasks/" + id)), 404, "Not found");
			isProblem(mvc.perform(get("/api/tasks/" + id + "/history")), 404, "Not found");
			org.assertj.core.api.Assertions.assertThat(db.queryForObject("SELECT count(*) FROM task_history", Integer.class)).isZero();
		}

		@Test
		void deletingAnUnknownTaskIsNotFound() throws Exception {
			isProblem(mvc.perform(delete("/api/tasks/999")), 404, "Not found");
		}
	}

	@Nested
	class Http {

		@Test
		void anUnsupportedMethodIsMethodNotAllowed() throws Exception {
			mvc.perform(patch("/api/tasks/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isMethodNotAllowed())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}

		@Test
		void anUnknownRouteIsNotFound() throws Exception {
			mvc.perform(get("/api/nothing-here"))
					.andExpect(status().isNotFound())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		}
	}
}
