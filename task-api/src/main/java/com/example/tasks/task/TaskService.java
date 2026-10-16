package com.example.tasks.task;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.tasks.error.NotFoundException;
import com.example.tasks.error.UnknownReferenceException;
import com.example.tasks.user.AppUser;
import com.example.tasks.user.UserRepository;

/** The task use cases. The rules live in {@link Task}; this class loads, saves and tells it the time. */
@Service
@Transactional
public class TaskService {

	private static final Sort BY_DUE_DATE = Sort.by("dueDate", "id");

	private final TaskRepository tasks;
	private final TaskHistoryRepository history;
	private final UserRepository users;
	private final Clock clock;

	public TaskService(TaskRepository tasks, TaskHistoryRepository history, UserRepository users, Clock clock) {
		this.tasks = tasks;
		this.history = history;
		this.users = users;
		this.clock = clock;
	}

	public Task create(String title, String description, Instant dueDate) {
		return tasks.save(Task.create(title, description, dueDate, now()));
	}

	@Transactional(readOnly = true)
	public Task get(long id) {
		return find(id);
	}

	@Transactional(readOnly = true)
	public Page<Task> list(TaskFilter filter, Pageable pageable) {
		var sorted = pageable.getSort().isSorted() ? pageable
				: org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), BY_DUE_DATE);
		return tasks.findAll(filter.toSpecification(now()), sorted);
	}

	/** Every task matching the filter, by due date: the board groups them into columns. */
	@Transactional(readOnly = true)
	public List<Task> board(TaskFilter filter) {
		return tasks.findAll(filter.toSpecification(now()), BY_DUE_DATE);
	}

	public Task updateDetails(long id, String title, String description, Instant dueDate) {
		var task = find(id);
		task.updateDetails(title, description, dueDate, now());
		return task;
	}

	public Task moveTo(long id, TaskStatus status) {
		var task = find(id);
		task.moveTo(status, now());
		return task;
	}

	public Task assign(long id, Long userId) {
		var task = find(id);
		task.assignTo(user(userId), now());
		return task;
	}

	public Task unassign(long id) {
		var task = find(id);
		task.unassign(now());
		return task;
	}

	/** Deletes the task and, with it, its history and notifications. */
	public void delete(long id) {
		tasks.delete(find(id));
	}

	@Transactional(readOnly = true)
	public List<TaskHistory> history(long id) {
		find(id);
		return history.findByTaskIdOrderByOccurredAtAscIdAsc(id);
	}

	public Instant now() {
		return clock.instant();
	}

	private Task find(long id) {
		return tasks.findById(id).orElseThrow(() -> new NotFoundException("Task", id));
	}

	private AppUser user(Long userId) {
		if (userId == null) {
			throw new InvalidTaskException(java.util.Map.of("userId", "is required"));
		}
		return users.findById(userId).orElseThrow(() -> new UnknownReferenceException("userId", "User %d doesn't exist".formatted(userId)));
	}
}
