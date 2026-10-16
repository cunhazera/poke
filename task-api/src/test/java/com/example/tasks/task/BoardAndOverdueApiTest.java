package com.example.tasks.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.tasks.notification.OverdueTaskNotifier;
import com.example.tasks.support.ApiTest;

/** The board, and what happens when a task reaches its due date: the warning and the (mock) notifications. */
class BoardAndOverdueApiTest extends ApiTest {

	@Autowired
	private OverdueTaskNotifier notifier;

	@Nested
	class Board {

		@Test
		void showsTheThreeColumnsInOrderWithTheirTasks() throws Exception {
			var started = createTask("started", TOMORROW.plusSeconds(2));
			createTask("waiting", TOMORROW.plusSeconds(1));
			createTask("first", TOMORROW);
			assign(started, ADA);
			move(started, "IN_PROGRESS");

			mvc.perform(get("/api/board"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.generatedAt").value("2026-10-16T12:00:00Z"))
					.andExpect(jsonPath("$.overdueCount").value(0))
					.andExpect(jsonPath("$.columns[*].status").value(contains("TODO", "IN_PROGRESS", "DONE")))
					.andExpect(jsonPath("$.columns[*].title").value(contains("To do", "In progress", "Done")))
					.andExpect(jsonPath("$.columns[0].count").value(2))
					.andExpect(jsonPath("$.columns[0].tasks[*].title").value(contains("first", "waiting")))
					.andExpect(jsonPath("$.columns[1].tasks[*].title").value(contains("started")))
					.andExpect(jsonPath("$.columns[2].tasks", hasSize(0)));
		}

		@Test
		void warnsAboutEveryOpenTaskPastItsDueDate() throws Exception {
			var late = createTask("late", NOW.plusSeconds(60));
			var finished = createTask("finished", NOW.plusSeconds(60));
			createTask("on time", TOMORROW);
			assign(finished, ADA);
			move(finished, "IN_PROGRESS");
			move(finished, "DONE");

			clock.advance(Duration.ofMinutes(1)); // exactly the due date: overdue from this moment

			mvc.perform(get("/api/board"))
					.andExpect(jsonPath("$.overdueCount").value(1))
					.andExpect(jsonPath("$.columns[0].tasks[0].title").value("late"))
					.andExpect(jsonPath("$.columns[0].tasks[0].overdue").value(true))
					.andExpect(jsonPath("$.columns[0].tasks[0].warning").value("Overdue: the planned due date (2026-10-16T12:01:00Z) has passed"))
					.andExpect(jsonPath("$.columns[0].tasks[1].overdue").value(false))
					.andExpect(jsonPath("$.columns[0].tasks[1].warning").value(nullValue()))
					.andExpect(jsonPath("$.columns[2].tasks[0].overdue").value(false));
			mvc.perform(get("/api/tasks/" + late)).andExpect(jsonPath("$.overdue").value(true));
		}

		@Test
		void showsOneUsersTasks() throws Exception {
			var mine = createTask("mine", TOMORROW);
			createTask("not mine", TOMORROW);
			assign(mine, GRACE);

			mvc.perform(get("/api/board").param("ownerId", String.valueOf(GRACE)))
					.andExpect(jsonPath("$.columns[0].tasks[*].title").value(contains("mine")));
		}

		@Test
		void aMalformedOwnerIsBadRequest() throws Exception {
			mvc.perform(get("/api/board").param("ownerId", "abc")).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.ownerId").exists());
			mvc.perform(get("/api/board").param("ownerId", "-3")).andExpect(status().isBadRequest());
		}
	}

	@Nested
	class Notifications {

		@Test
		void notifiesTheBoardAdminAndTheOwnerOnceTheDueDateIsReached() throws Exception {
			var id = createTask("Ship it", NOW.plusSeconds(60));
			assign(id, ALAN);

			assertThat(notifier.notifyOverdueTasks()).as("not due yet").isZero();
			clock.advance(Duration.ofMinutes(1));
			assertThat(notifier.notifyOverdueTasks()).isEqualTo(1);

			mvc.perform(get("/api/notifications"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$", hasSize(2)))
					.andExpect(jsonPath("$[*].recipient.username").value(containsInAnyOrder("ada", "alan")))
					.andExpect(jsonPath("$[0].taskId").value(id))
					.andExpect(jsonPath("$[0].message").value("Task #1 \"Ship it\" is overdue: it was due 2026-10-16T12:01:00Z"))
					.andExpect(jsonPath("$[0].sentAt").value("2026-10-16T12:01:00Z"));
			mvc.perform(get("/api/notifications").param("recipientId", String.valueOf(ALAN)))
					.andExpect(jsonPath("$", hasSize(1)));
			mvc.perform(get("/api/tasks/" + id + "/history"))
					.andExpect(jsonPath("$[-1:].action").value(contains("OVERDUE_NOTIFIED")))
					.andExpect(jsonPath("$[-1:].occurredAt").value(contains("2026-10-16T12:01:00Z")));
		}

		@Test
		void notifiesOncePerDueDate() throws Exception {
			var id = createTask("t", NOW.plusSeconds(60));
			assign(id, ALAN);
			clock.advance(Duration.ofMinutes(1));
			notifier.notifyOverdueTasks();

			clock.advance(Duration.ofHours(1));
			assertThat(notifier.notifyOverdueTasks()).isZero();

			mvc.perform(get("/api/notifications")).andExpect(jsonPath("$", hasSize(2)));
		}

		@Test
		void aNewDueDateIsNotifiedAgainWhenItPasses() throws Exception {
			var id = createTask("t", NOW.plusSeconds(60));
			clock.advance(Duration.ofMinutes(1));
			notifier.notifyOverdueTasks();

			putJson("/api/tasks/" + id, task("t", null, NOW.plus(Duration.ofHours(2)))).andExpect(status().isOk());
			mvc.perform(get("/api/tasks/" + id)).andExpect(jsonPath("$.overdue").value(false));
			clock.advance(Duration.ofHours(2));

			assertThat(notifier.notifyOverdueTasks()).isEqualTo(1);
		}

		@Test
		void anUnassignedTaskOnlyNotifiesTheAdmin() throws Exception {
			createTask("nobody's", NOW.plusSeconds(60));
			clock.advance(Duration.ofMinutes(1));

			notifier.notifyOverdueTasks();

			mvc.perform(get("/api/notifications")).andExpect(jsonPath("$[*].recipient.username").value(contains("ada")));
		}

		@Test
		void anAdminWhoOwnsTheTaskIsNotifiedOnce() throws Exception {
			var id = createTask("ada's", NOW.plusSeconds(60));
			assign(id, ADA);
			clock.advance(Duration.ofMinutes(1));

			notifier.notifyOverdueTasks();

			mvc.perform(get("/api/notifications")).andExpect(jsonPath("$[*].recipient.username").value(contains("ada")));
		}

		@Test
		void aDoneTaskIsNeverNotified() throws Exception {
			var id = createTask("t", NOW.plusSeconds(60));
			assign(id, ALAN);
			move(id, "IN_PROGRESS");
			move(id, "DONE");
			clock.advance(Duration.ofDays(1));

			assertThat(notifier.notifyOverdueTasks()).isZero();
			mvc.perform(get("/api/notifications")).andExpect(jsonPath("$", hasSize(0)));
		}

		@Test
		void aMalformedRecipientIsBadRequest() throws Exception {
			mvc.perform(get("/api/notifications").param("recipientId", "abc")).andExpect(status().isBadRequest());
		}
	}
}
