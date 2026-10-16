package com.example.tasks.task;

import static com.example.tasks.task.TaskStatus.DONE;
import static com.example.tasks.task.TaskStatus.IN_PROGRESS;
import static com.example.tasks.task.TaskStatus.TODO;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskStatusTest {

	/** The whole matrix: one column at a time, both ways; never a jump, never "moving" to the same column. */
	@ParameterizedTest(name = "{0} → {1}: {2}")
	@CsvSource({
			"TODO,        IN_PROGRESS, true",
			"IN_PROGRESS, DONE,        true",
			"IN_PROGRESS, TODO,        true",
			"DONE,        IN_PROGRESS, true",
			"TODO,        DONE,        false",
			"DONE,        TODO,        false",
			"TODO,        TODO,        false",
			"IN_PROGRESS, IN_PROGRESS, false",
			"DONE,        DONE,        false" })
	void movesOnlyToTheNeighbouringColumn(TaskStatus from, TaskStatus to, boolean allowed) {
		assertThat(from.canMoveTo(to)).isEqualTo(allowed);
	}

	@org.junit.jupiter.api.Test
	void columnsAreInBoardOrder() {
		assertThat(TaskStatus.values()).containsExactly(TODO, IN_PROGRESS, DONE);
	}
}
