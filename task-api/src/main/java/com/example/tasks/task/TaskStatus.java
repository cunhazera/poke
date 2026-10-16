package com.example.tasks.task;

/**
 * The board's columns, in order. A task moves one column at a time, forward or backward: Todo ⇄ In Progress ⇄ Done.
 * So it can't jump from Todo straight to Done, and a done task goes back to In Progress (not to Todo).
 */
public enum TaskStatus {

	TODO("To do"),
	IN_PROGRESS("In progress"),
	DONE("Done");

	private final String label;

	TaskStatus(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	/** Whether a task in this status may move to {@code target}: only to the neighbouring column. */
	public boolean canMoveTo(TaskStatus target) {
		return Math.abs(ordinal() - target.ordinal()) == 1;
	}
}
