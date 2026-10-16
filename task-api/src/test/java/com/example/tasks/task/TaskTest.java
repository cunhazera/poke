package com.example.tasks.task;

import static com.example.tasks.task.TaskStatus.DONE;
import static com.example.tasks.task.TaskStatus.IN_PROGRESS;
import static com.example.tasks.task.TaskStatus.TODO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.tasks.user.AppUser;

/** The task's business rules, without Spring or a database: time is passed in, so every boundary is exact. */
class TaskTest {

	private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
	private static final Instant TOMORROW = NOW.plus(Duration.ofDays(1));
	private static final AppUser ADA = new AppUser(1L, "Ada Lovelace", "ada", "ada@example.com");
	private static final AppUser ALAN = new AppUser(2L, "Alan Turing", "alan", "alan@example.com");

	private static Task newTask() {
		return Task.create("Write the report", "Quarterly numbers", TOMORROW, NOW);
	}

	private static Task inProgress() {
		var task = newTask();
		task.assignTo(ADA, NOW);
		task.moveTo(IN_PROGRESS, NOW);
		return task;
	}

	private static InvalidTaskException invalid(Runnable action) {
		try {
			action.run();
		} catch (InvalidTaskException e) {
			return e;
		}
		throw new AssertionError("expected an InvalidTaskException");
	}

	@Nested
	class Creating {

		@Test
		void startsInTodoWithoutAnOwner() {
			var task = newTask();

			assertThat(task.getStatus()).isEqualTo(TODO);
			assertThat(task.getOwner()).isNull();
			assertThat(task.getTitle()).isEqualTo("Write the report");
			assertThat(task.getDescription()).isEqualTo("Quarterly numbers");
			assertThat(task.getDueDate()).isEqualTo(TOMORROW);
			assertThat(task.getCreatedAt()).isEqualTo(NOW);
			assertThat(task.getUpdatedAt()).isEqualTo(NOW);
		}

		@Test
		void recordsItsCreationInTheHistory() {
			assertThat(newTask().getHistory()).extracting(TaskHistory::getAction, TaskHistory::getToStatus, TaskHistory::getOccurredAt)
					.containsExactly(tuple(TaskAction.CREATED, TODO, NOW));
		}

		@Test
		void trimsTheTitle() {
			assertThat(Task.create("  Plan the sprint  ", null, TOMORROW, NOW).getTitle()).isEqualTo("Plan the sprint");
		}

		@Test
		void acceptsATitleOf124CharactersButNot125() {
			assertThat(Task.create("x".repeat(124), null, TOMORROW, NOW).getTitle()).hasSize(124);
			assertThat(invalid(() -> Task.create("x".repeat(125), null, TOMORROW, NOW)).errors())
					.containsEntry("title", "must be at most 124 characters");
		}

		@Test
		void countsCharactersNotUtf16Units() {
			var emoji = "🚀".repeat(124); // 248 UTF-16 units, 124 characters, like PostgreSQL's char_length

			assertThat(Task.create(emoji, null, TOMORROW, NOW).getTitle()).isEqualTo(emoji);
		}

		@ParameterizedTest
		@NullSource
		@ValueSource(strings = { "", "   ", "\t\n" })
		void requiresATitle(String title) {
			assertThat(invalid(() -> Task.create(title, null, TOMORROW, NOW)).errors()).containsKey("title");
		}

		@Test
		void hasNoDescriptionLimitAndTreatsABlankOneAsNone() {
			var longText = "a".repeat(100_000);

			assertThat(Task.create("t", longText, TOMORROW, NOW).getDescription()).hasSize(100_000);
			assertThat(Task.create("t", "   ", TOMORROW, NOW).getDescription()).isNull();
			assertThat(Task.create("t", null, TOMORROW, NOW).getDescription()).isNull();
		}

		@Test
		void requiresADueDateInTheFuture() {
			assertThat(invalid(() -> Task.create("t", null, null, NOW)).errors()).containsEntry("dueDate", "is required");
			assertThat(invalid(() -> Task.create("t", null, NOW, NOW)).errors()).containsEntry("dueDate", "must be in the future");
			assertThat(invalid(() -> Task.create("t", null, NOW.minusSeconds(1), NOW)).errors()).containsKey("dueDate");
			assertThat(Task.create("t", null, NOW.plusSeconds(1), NOW).getDueDate()).isEqualTo(NOW.plusSeconds(1));
		}

		@Test
		void reportsEveryBrokenRuleAtOnce() {
			assertThat(invalid(() -> Task.create(" ", null, null, NOW)).errors()).containsOnlyKeys("title", "dueDate");
		}
	}

	@Nested
	class Moving {

		@Test
		void aTaskNeedsAnOwnerBeforeWorkStarts() {
			var task = newTask();

			assertThatThrownBy(() -> task.moveTo(IN_PROGRESS, NOW)).isInstanceOf(TaskRuleException.class)
					.hasMessageContaining("Assign an owner");
			assertThat(task.getStatus()).isEqualTo(TODO);
		}

		@Test
		void movesForwardOneColumnAtATimeRecordingEachMove() {
			var task = newTask();
			task.assignTo(ADA, NOW);
			var later = NOW.plusSeconds(60);

			task.moveTo(IN_PROGRESS, NOW);
			task.moveTo(DONE, later);

			assertThat(task.getStatus()).isEqualTo(DONE);
			assertThat(task.getUpdatedAt()).isEqualTo(later);
			assertThat(task.getHistory()).filteredOn(h -> h.getAction() == TaskAction.STATUS_CHANGED)
					.extracting(TaskHistory::getFromStatus, TaskHistory::getToStatus, TaskHistory::getOccurredAt)
					.containsExactly(tuple(TODO, IN_PROGRESS, NOW), tuple(IN_PROGRESS, DONE, later));
		}

		@Test
		void movesBackward() {
			var task = inProgress();
			task.moveTo(DONE, NOW);

			task.moveTo(IN_PROGRESS, NOW);
			task.moveTo(TODO, NOW);

			assertThat(task.getStatus()).isEqualTo(TODO);
		}

		@Test
		void cannotJumpFromTodoToDone() {
			var task = newTask();
			task.assignTo(ADA, NOW);

			assertThatThrownBy(() -> task.moveTo(DONE, NOW)).isInstanceOf(TaskRuleException.class)
					.hasMessage("A task can't move from To do to Done: move it to In progress first");
			assertThat(task.getStatus()).isEqualTo(TODO);
		}

		@Test
		void cannotJumpFromDoneBackToTodo() {
			var task = inProgress();
			task.moveTo(DONE, NOW);

			assertThatThrownBy(() -> task.moveTo(TODO, NOW)).isInstanceOf(TaskRuleException.class)
					.hasMessage("A task can't move from Done to To do: move it to In progress first");
		}

		@Test
		void movingToTheSameStatusIsRejected() {
			var task = inProgress();
			var historySize = task.getHistory().size();

			assertThatThrownBy(() -> task.moveTo(IN_PROGRESS, NOW)).isInstanceOf(TaskRuleException.class)
					.hasMessage("The task is already In progress");
			assertThat(task.getHistory()).hasSize(historySize);
		}

		@Test
		void requiresATargetStatus() {
			assertThat(invalid(() -> inProgress().moveTo(null, NOW)).errors()).containsEntry("status", "is required");
		}
	}

	@Nested
	class Owning {

		@Test
		void assigningRecordsTheChange() {
			var task = newTask();

			task.assignTo(ADA, NOW);
			task.assignTo(ALAN, NOW.plusSeconds(5));

			assertThat(task.getOwner()).isSameAs(ALAN);
			assertThat(task.getHistory()).filteredOn(h -> h.getAction() == TaskAction.OWNER_CHANGED)
					.extracting(TaskHistory::getFromOwner, TaskHistory::getToOwner)
					.containsExactly(tuple(null, ADA), tuple(ADA, ALAN));
		}

		@Test
		void reassigningToTheSameOwnerChangesNothing() {
			var task = newTask();
			task.assignTo(ADA, NOW);
			var historySize = task.getHistory().size();

			assertThat(task.assignTo(ADA, NOW.plusSeconds(5))).isFalse();
			assertThat(task.getHistory()).hasSize(historySize);
		}

		@Test
		void aTaskInProgressCanChangeHandsButNotBeLeftWithoutAnOwner() {
			var task = inProgress();

			task.assignTo(ALAN, NOW);
			assertThat(task.getOwner()).isSameAs(ALAN);
			assertThatThrownBy(() -> task.unassign(NOW)).isInstanceOf(TaskRuleException.class)
					.hasMessageContaining("only a task in To do can be left without an owner");
		}

		@Test
		void aTodoTaskCanBeUnassigned() {
			var task = newTask();
			task.assignTo(ADA, NOW);

			assertThat(task.unassign(NOW)).isTrue();

			assertThat(task.getOwner()).isNull();
			assertThat(task.getHistory()).last().extracting(TaskHistory::getAction, TaskHistory::getFromOwner, TaskHistory::getToOwner)
					.containsExactly(TaskAction.OWNER_CHANGED, ADA, null);
		}

		@Test
		void unassigningATaskWithoutAnOwnerChangesNothing() {
			var task = newTask();

			assertThat(task.unassign(NOW)).isFalse();
			assertThat(task.getHistory()).hasSize(1);
		}

		@Test
		void requiresAUser() {
			assertThatThrownBy(() -> newTask().assignTo(null, NOW)).isInstanceOf(NullPointerException.class);
		}
	}

	@Nested
	class Editing {

		@Test
		void replacesTheDetailsAndRecordsIt() {
			var task = newTask();
			var later = NOW.plusSeconds(30);

			assertThat(task.updateDetails("New title", null, TOMORROW.plusSeconds(60), later)).isTrue();

			assertThat(task.getTitle()).isEqualTo("New title");
			assertThat(task.getDescription()).isNull();
			assertThat(task.getDueDate()).isEqualTo(TOMORROW.plusSeconds(60));
			assertThat(task.getUpdatedAt()).isEqualTo(later);
			assertThat(task.getHistory()).last().extracting(TaskHistory::getAction).isEqualTo(TaskAction.DETAILS_UPDATED);
		}

		@Test
		void sendingTheSameDetailsChangesNothing() {
			var task = newTask();

			assertThat(task.updateDetails(" Write the report ", "Quarterly numbers", TOMORROW, NOW.plusSeconds(30))).isFalse();
			assertThat(task.getHistory()).hasSize(1);
			assertThat(task.getUpdatedAt()).isEqualTo(NOW);
		}

		@Test
		void validatesLikeCreating() {
			var task = newTask();

			var errors = invalid(() -> task.updateDetails("", null, NOW.minusSeconds(1), NOW)).errors();

			assertThat(errors).containsOnlyKeys("title", "dueDate");
			assertThat(task.getTitle()).isEqualTo("Write the report");
		}

		@Test
		void anOverdueTaskKeepsItsPastDueDateWhenOtherDetailsChange() {
			var task = newTask();
			var afterDue = TOMORROW.plusSeconds(1);

			assertThat(task.updateDetails("Renamed", "x", TOMORROW, afterDue)).isTrue();
			assertThat(task.getDueDate()).isEqualTo(TOMORROW);
		}

		@Test
		void aNewDueDateMustBeInTheFuture() {
			var task = newTask();

			assertThat(invalid(() -> task.updateDetails("t", null, NOW, NOW)).errors()).containsKey("dueDate");
		}
	}

	@Nested
	class Overdue {

		@Test
		void isOverdueFromTheMomentItReachesItsDueDateUnlessDone() {
			var task = inProgress();

			assertThat(task.isOverdue(TOMORROW.minusMillis(1))).isFalse();
			assertThat(task.isOverdue(TOMORROW)).isTrue();
			assertThat(task.isOverdue(TOMORROW.plusSeconds(3600))).isTrue();

			task.moveTo(DONE, NOW);
			assertThat(task.isOverdue(TOMORROW.plusSeconds(3600))).isFalse();
		}

		@Test
		void anOverdueTaskCarriesAWarning() {
			var task = newTask();

			assertThat(task.overdueWarning(NOW)).isNull();
			assertThat(task.overdueWarning(TOMORROW)).isEqualTo("Overdue: the planned due date (2026-10-17T12:00:00Z) has passed");
		}

		@Test
		void needsANotificationOnceUntilItIsSent() {
			var task = inProgress();

			assertThat(task.needsOverdueNotification(NOW)).isFalse();
			assertThat(task.needsOverdueNotification(TOMORROW)).isTrue();

			task.markOverdueNotified(TOMORROW);

			assertThat(task.needsOverdueNotification(TOMORROW.plusSeconds(60))).isFalse();
			assertThat(task.getHistory()).last().extracting(TaskHistory::getAction, TaskHistory::getOccurredAt)
					.containsExactly(TaskAction.OVERDUE_NOTIFIED, TOMORROW);
		}

		@Test
		void aNewDueDateArmsTheNotificationAgain() {
			var task = inProgress();
			task.markOverdueNotified(TOMORROW);
			var nextWeek = TOMORROW.plus(Duration.ofDays(7));

			task.updateDetails(task.getTitle(), null, nextWeek, TOMORROW.plusSeconds(60));

			assertThat(task.needsOverdueNotification(nextWeek)).isTrue();
		}

		@Test
		void aDoneTaskNeverNeedsANotification() {
			var task = inProgress();
			task.moveTo(DONE, NOW);

			assertThat(task.needsOverdueNotification(TOMORROW)).isFalse();
		}
	}
}
