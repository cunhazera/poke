package com.example.tasks.task;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import com.example.tasks.support.ApiTest;

/** Moving tasks on the board and assigning them, over HTTP, with their history. */
class TaskWorkflowApiTest extends ApiTest {

	private org.springframework.test.web.servlet.ResultActions moveTo(long id, Object status) throws Exception {
		return putJson("/api/tasks/" + id + "/status", status == null ? "{}" : json(Map.of("status", status)));
	}

	@Nested
	class Moving {

		@Test
		void movesForwardAndBackwardOneColumnAtATime() throws Exception {
			var id = createTask("t", TOMORROW);
			assign(id, ADA);

			moveTo(id, "IN_PROGRESS").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
			moveTo(id, "DONE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
			moveTo(id, "IN_PROGRESS").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
			moveTo(id, "TODO").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TODO"));
		}

		@Test
		void eachMoveIsRecordedWithItsTime() throws Exception {
			var id = createTask("t", TOMORROW);
			assign(id, ADA);
			clock.advance(Duration.ofMinutes(10));
			move(id, "IN_PROGRESS");
			clock.advance(Duration.ofMinutes(10));
			move(id, "DONE");

			mvc.perform(get("/api/tasks/" + id + "/history"))
					.andExpect(jsonPath("$[*].action").value(contains("CREATED", "OWNER_CHANGED", "STATUS_CHANGED", "STATUS_CHANGED")))
					.andExpect(jsonPath("$[2].fromStatus").value("TODO"))
					.andExpect(jsonPath("$[2].toStatus").value("IN_PROGRESS"))
					.andExpect(jsonPath("$[2].occurredAt").value("2026-10-16T12:10:00Z"))
					.andExpect(jsonPath("$[3].fromStatus").value("IN_PROGRESS"))
					.andExpect(jsonPath("$[3].toStatus").value("DONE"))
					.andExpect(jsonPath("$[3].occurredAt").value("2026-10-16T12:20:00Z"));
		}

		@Test
		void cannotGoFromTodoStraightToDone() throws Exception {
			var id = createTask("t", TOMORROW);
			assign(id, ADA);

			moveTo(id, "DONE").andExpect(status().isConflict())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.title").value("Not allowed"))
					.andExpect(jsonPath("$.detail").value("A task can't move from To do to Done: move it to In progress first"));
			mvc.perform(get("/api/tasks/" + id)).andExpect(jsonPath("$.status").value("TODO"));
		}

		@Test
		void cannotGoFromDoneStraightBackToTodo() throws Exception {
			var id = createTask("t", TOMORROW);
			assign(id, ADA);
			move(id, "IN_PROGRESS");
			move(id, "DONE");

			moveTo(id, "TODO").andExpect(status().isConflict())
					.andExpect(jsonPath("$.detail").value("A task can't move from Done to To do: move it to In progress first"));
		}

		@Test
		void movingToTheCurrentStatusIsAConflict() throws Exception {
			var id = createTask("t", TOMORROW);

			moveTo(id, "TODO").andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("The task is already To do"));
		}

		@Test
		void workCannotStartWithoutAnOwner() throws Exception {
			var id = createTask("t", TOMORROW);

			moveTo(id, "IN_PROGRESS").andExpect(status().isConflict())
					.andExpect(jsonPath("$.detail").value("Assign an owner before moving the task to In progress"));
			mvc.perform(get("/api/tasks/" + id + "/history")).andExpect(jsonPath("$", hasSize(1)));
		}

		@Test
		void anUnknownStatusListsTheValidOnes() throws Exception {
			var id = createTask("t", TOMORROW);

			moveTo(id, "FINISHED").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.title").value("Invalid field value"))
					.andExpect(jsonPath("$.errors.status").value("must be one of TODO, IN_PROGRESS, DONE"));
		}

		@Test
		void theStatusIsRequired() throws Exception {
			var id = createTask("t", TOMORROW);

			moveTo(id, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.status").value("is required"));
		}

		@Test
		void anUnknownTaskIsNotFound() throws Exception {
			moveTo(999, "IN_PROGRESS").andExpect(status().isNotFound());
		}
	}

	@Nested
	class Owning {

		@Test
		void assignsAnOwnerAndRecordsIt() throws Exception {
			var id = createTask("t", TOMORROW);

			putJson("/api/tasks/" + id + "/owner", Map.of("userId", ADA))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.owner.id").value(ADA))
					.andExpect(jsonPath("$.owner.username").value("ada"))
					.andExpect(jsonPath("$.owner.fullName").value("Ada Lovelace"));
			mvc.perform(get("/api/tasks/" + id + "/history"))
					.andExpect(jsonPath("$[1].action").value("OWNER_CHANGED"))
					.andExpect(jsonPath("$[1].fromOwner").value(nullValue()))
					.andExpect(jsonPath("$[1].toOwner.username").value("ada"));
		}

		@Test
		void reassigningReplacesTheOwnerAndRecordsBoth() throws Exception {
			var id = createTask("t", TOMORROW);
			assign(id, ADA);

			assign(id, ALAN);

			mvc.perform(get("/api/tasks/" + id)).andExpect(jsonPath("$.owner.username").value("alan"));
			mvc.perform(get("/api/tasks/" + id + "/history"))
					.andExpect(jsonPath("$[2].fromOwner.username").value("ada"))
					.andExpect(jsonPath("$[2].toOwner.username").value("alan"));
		}

		@Test
		void assigningTheSameOwnerAgainRecordsNothing() throws Exception {
			var id = createTask("t", TOMORROW);
			assign(id, ADA);

			assign(id, ADA);

			mvc.perform(get("/api/tasks/" + id + "/history")).andExpect(jsonPath("$", hasSize(2)));
		}

		@Test
		void oneUserOwnsManyTasks() throws Exception {
			var first = createTask("first", TOMORROW);
			var second = createTask("second", TOMORROW.plusSeconds(1));
			assign(first, GRACE);
			assign(second, GRACE);

			mvc.perform(get("/api/tasks").param("ownerId", String.valueOf(GRACE)))
					.andExpect(jsonPath("$.items[*].title").value(contains("first", "second")));
		}

		@Test
		void anUnknownUserIsUnprocessable() throws Exception {
			var id = createTask("t", TOMORROW);

			putJson("/api/tasks/" + id + "/owner", Map.of("userId", 999))
					.andExpect(status().isUnprocessableContent())
					.andExpect(jsonPath("$.title").value("Unknown reference"))
					.andExpect(jsonPath("$.errors.userId").value("User 999 doesn't exist"));
			mvc.perform(get("/api/tasks/" + id)).andExpect(jsonPath("$.owner").value(nullValue()));
		}

		@Test
		void theUserIsRequired() throws Exception {
			var id = createTask("t", TOMORROW);

			putJson("/api/tasks/" + id + "/owner", "{}").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.userId").value("is required"));
		}

		@ParameterizedTest
		@ValueSource(strings = { "\"ada\"", "1.5", "true" })
		void theUserIsAnId(String userId) throws Exception {
			var id = createTask("t", TOMORROW);

			putJson("/api/tasks/" + id + "/owner", "{\"userId\": " + userId + "}").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.userId").exists());
		}

		@Test
		void aTodoTaskCanBeUnassigned() throws Exception {
			var id = createTask("t", TOMORROW);
			assign(id, ADA);

			mvc.perform(delete("/api/tasks/" + id + "/owner"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.owner").value(nullValue()));
			mvc.perform(get("/api/tasks/" + id + "/history"))
					.andExpect(jsonPath("$[2].fromOwner.username").value("ada"))
					.andExpect(jsonPath("$[2].toOwner").value(nullValue()));
		}

		@Test
		void workInProgressCannotBeLeftWithoutAnOwner() throws Exception {
			var id = createTask("t", TOMORROW);
			assign(id, ADA);
			move(id, "IN_PROGRESS");

			mvc.perform(delete("/api/tasks/" + id + "/owner")).andExpect(status().isConflict())
					.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Reassign it instead")));
			mvc.perform(get("/api/tasks/" + id)).andExpect(jsonPath("$.owner.username").value("ada"));
		}

		@Test
		void unknownTasksAreNotFound() throws Exception {
			putJson("/api/tasks/999/owner", Map.of("userId", ADA)).andExpect(status().isNotFound());
			mvc.perform(delete("/api/tasks/999/owner")).andExpect(status().isNotFound());
		}
	}
}
