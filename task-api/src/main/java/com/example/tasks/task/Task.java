package com.example.tasks.task;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

import com.example.tasks.user.AppUser;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * A task on the board, and the rules for changing it. Every change goes through a method here, which checks the
 * rules and appends to the task's history; the current time is passed in, so the rules are testable without a clock.
 */
@Entity
@Table(name = "task")
public class Task {

	public static final int MAX_TITLE_LENGTH = 124;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, columnDefinition = "text")
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(nullable = false, columnDefinition = "task_status")
	private TaskStatus status = TaskStatus.TODO;

	@Column(name = "due_date", nullable = false)
	private Instant dueDate;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private AppUser owner;

	@Column(name = "overdue_notified_at")
	private Instant overdueNotifiedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Appended to by the methods below; read through the history repository (in order) by the API. */
	@OneToMany(mappedBy = "task", cascade = CascadeType.PERSIST)
	private List<TaskHistory> history = new ArrayList<>();

	protected Task() {
	}

	/** A new task: in To do, with no owner (one is assigned afterwards), and a due date in the future. */
	public static Task create(String title, String description, Instant dueDate, Instant now) {
		var errors = new LinkedHashMap<String, String>();
		var cleanTitle = checkTitle(title, errors);
		checkNewDueDate(dueDate, now, errors);
		failIfAny(errors);

		var task = new Task();
		task.title = cleanTitle;
		task.description = cleanDescription(description);
		task.dueDate = dueDate;
		task.createdAt = now;
		task.updatedAt = now;
		task.record(TaskAction.CREATED, null, TaskStatus.TODO, null, null, now);
		return task;
	}

	/**
	 * Replaces the title, description and due date. A changed due date must be in the future (and arms the overdue
	 * notification again); an unchanged one may already have passed, so an overdue task can still be renamed.
	 *
	 * @return whether anything changed (nothing is recorded otherwise)
	 */
	public boolean updateDetails(String newTitle, String newDescription, Instant newDueDate, Instant now) {
		var errors = new LinkedHashMap<String, String>();
		var cleanTitle = checkTitle(newTitle, errors);
		var dueDateChanged = newDueDate == null || !newDueDate.equals(dueDate);
		if (dueDateChanged) {
			checkNewDueDate(newDueDate, now, errors);
		}
		failIfAny(errors);

		var cleanDescription = cleanDescription(newDescription);
		if (cleanTitle.equals(title) && Objects.equals(cleanDescription, description) && !dueDateChanged) {
			return false;
		}
		title = cleanTitle;
		description = cleanDescription;
		if (dueDateChanged) {
			dueDate = newDueDate;
			overdueNotifiedAt = null;
		}
		record(TaskAction.DETAILS_UPDATED, null, null, null, null, now);
		return true;
	}

	/** Moves the task one column, forward or backward. Work can't start (or finish) without an owner. */
	public void moveTo(TaskStatus target, Instant now) {
		if (target == null) {
			throw new InvalidTaskException(Map.of("status", "is required"));
		}
		if (target == status) {
			throw new TaskRuleException("The task is already " + status.label());
		}
		if (!status.canMoveTo(target)) {
			throw new TaskRuleException("A task can't move from %s to %s: move it to %s first"
					.formatted(status.label(), target.label(), TaskStatus.IN_PROGRESS.label()));
		}
		if (target != TaskStatus.TODO && owner == null) {
			throw new TaskRuleException("Assign an owner before moving the task to " + target.label());
		}
		var from = status;
		status = target;
		record(TaskAction.STATUS_CHANGED, from, target, owner, owner, now);
	}

	/**
	 * Makes {@code user} the task's one owner, replacing any previous one.
	 *
	 * @return whether the owner changed
	 */
	public boolean assignTo(AppUser user, Instant now) {
		Objects.requireNonNull(user, "user");
		if (owner != null && Objects.equals(owner.getId(), user.getId())) {
			return false;
		}
		var from = owner;
		owner = user;
		record(TaskAction.OWNER_CHANGED, status, status, from, user, now);
		return true;
	}

	/**
	 * Removes the owner. Only a task in To do can be left without one: work in progress or done always has an owner.
	 *
	 * @return whether there was an owner to remove
	 */
	public boolean unassign(Instant now) {
		if (owner == null) {
			return false;
		}
		if (status != TaskStatus.TODO) {
			throw new TaskRuleException("The task is %s: only a task in %s can be left without an owner. Reassign it instead"
					.formatted(status.label(), TaskStatus.TODO.label()));
		}
		var from = owner;
		owner = null;
		record(TaskAction.OWNER_CHANGED, status, status, from, null, now);
		return true;
	}

	/** An open task (not Done) is overdue from the moment its due date is reached. */
	public boolean isOverdue(Instant now) {
		return status != TaskStatus.DONE && !dueDate.isAfter(now);
	}

	/** The warning shown on the board for an overdue task; null when it isn't. */
	public String overdueWarning(Instant now) {
		return isOverdue(now) ? "Overdue: the planned due date (%s) has passed".formatted(dueDate) : null;
	}

	/** Overdue, and the board admin and owner haven't been told yet about this due date. */
	public boolean needsOverdueNotification(Instant now) {
		return isOverdue(now) && overdueNotifiedAt == null;
	}

	public void markOverdueNotified(Instant now) {
		overdueNotifiedAt = now;
		history.add(new TaskHistory(this, TaskAction.OVERDUE_NOTIFIED, status, status, owner, owner, now));
	}

	private void record(TaskAction action, TaskStatus fromStatus, TaskStatus toStatus, AppUser fromOwner,
			AppUser toOwner, Instant now) {
		history.add(new TaskHistory(this, action, fromStatus, toStatus, fromOwner, toOwner, now));
		updatedAt = now;
	}

	private static String checkTitle(String title, Map<String, String> errors) {
		var clean = title == null ? "" : title.strip();
		if (clean.isEmpty()) {
			errors.put("title", "is required");
		} else if (clean.codePointCount(0, clean.length()) > MAX_TITLE_LENGTH) {
			// Characters, not UTF-16 units: the same count as PostgreSQL's char_length in the CHECK constraint.
			errors.put("title", "must be at most %d characters".formatted(MAX_TITLE_LENGTH));
		}
		return clean;
	}

	private static void checkNewDueDate(Instant dueDate, Instant now, Map<String, String> errors) {
		if (dueDate == null) {
			errors.put("dueDate", "is required");
		} else if (!dueDate.isAfter(now)) {
			errors.put("dueDate", "must be in the future");
		}
	}

	private static String cleanDescription(String description) {
		return description == null || description.isBlank() ? null : description;
	}

	private static void failIfAny(Map<String, String> errors) {
		if (!errors.isEmpty()) {
			throw new InvalidTaskException(errors);
		}
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public Instant getDueDate() {
		return dueDate;
	}

	public AppUser getOwner() {
		return owner;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	/** This instance's history, for unit tests; the API reads it from the repository, in order. */
	List<TaskHistory> getHistory() {
		return List.copyOf(history);
	}
}
