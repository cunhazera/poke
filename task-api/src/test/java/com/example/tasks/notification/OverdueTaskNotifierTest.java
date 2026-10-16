package com.example.tasks.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.tasks.task.Task;
import com.example.tasks.user.AppUser;

/** Who hears about an overdue task: the board admin and the owner, each once. */
class OverdueTaskNotifierTest {

	private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
	private static final AppUser ADMIN = new AppUser(1L, "Ada Lovelace", "ada", "ada@example.com");
	private static final AppUser ALAN = new AppUser(2L, "Alan Turing", "alan", "alan@example.com");

	private static Task task(AppUser owner) {
		var task = Task.create("t", null, NOW.plusSeconds(60), NOW);
		if (owner != null) {
			task.assignTo(owner, NOW);
		}
		return task;
	}

	@Test
	void theAdminAndTheOwner() {
		assertThat(OverdueTaskNotifier.recipients(task(ALAN), Optional.of(ADMIN))).containsExactly(ADMIN, ALAN);
	}

	@Test
	void anAdminWhoOwnsTheTaskOnlyOnce() {
		var sameAdminLoadedAgain = new AppUser(1L, "Ada Lovelace", "ada", "ada@example.com");

		assertThat(OverdueTaskNotifier.recipients(task(sameAdminLoadedAgain), Optional.of(ADMIN))).containsExactly(ADMIN);
	}

	@Test
	void onlyTheAdminWhenNobodyOwnsIt() {
		assertThat(OverdueTaskNotifier.recipients(task(null), Optional.of(ADMIN))).containsExactly(ADMIN);
	}

	@Test
	void onlyTheOwnerWhenTheConfiguredAdminDoesNotExist() {
		assertThat(OverdueTaskNotifier.recipients(task(ALAN), Optional.empty())).containsExactly(ALAN);
		assertThat(OverdueTaskNotifier.recipients(task(null), Optional.empty())).isEmpty();
	}
}
