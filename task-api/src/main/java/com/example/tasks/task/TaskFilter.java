package com.example.tasks.task;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;

/**
 * Optional filters for the task list and the board, combined with AND.
 *
 * @param ownerId only tasks owned by this user
 * @param unassigned true: only tasks without an owner; false: only tasks with one
 * @param overdue true: only overdue tasks; false: only tasks that aren't
 */
public record TaskFilter(TaskStatus status, Long ownerId, Boolean unassigned, Boolean overdue) {

	public static final TaskFilter NONE = new TaskFilter(null, null, null, null);

	Specification<Task> toSpecification(Instant now) {
		Specification<Task> spec = (root, query, cb) -> cb.conjunction();
		if (status != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
		}
		if (ownerId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId));
		}
		if (unassigned != null) {
			spec = spec.and((root, query, cb) -> unassigned ? cb.isNull(root.get("owner")) : cb.isNotNull(root.get("owner")));
		}
		if (overdue != null) {
			Specification<Task> isOverdue = (root, query, cb) -> cb.and(
					cb.notEqual(root.get("status"), TaskStatus.DONE),
					cb.lessThanOrEqualTo(root.get("dueDate"), now));
			spec = spec.and(overdue ? isOverdue : Specification.not(isOverdue));
		}
		return spec;
	}
}
