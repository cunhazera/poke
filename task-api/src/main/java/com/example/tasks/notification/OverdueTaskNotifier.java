package com.example.tasks.notification;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.tasks.config.BoardProperties;
import com.example.tasks.task.Task;
import com.example.tasks.task.TaskRepository;
import com.example.tasks.user.AppUser;
import com.example.tasks.user.UserRepository;

/**
 * Tells the board admin and the task's owner when an open task reaches its due date: once per due date (a new due
 * date arms it again), and never for a task that is Done.
 */
@Service
public class OverdueTaskNotifier {

	private static final Logger log = LoggerFactory.getLogger(OverdueTaskNotifier.class);

	private final TaskRepository tasks;
	private final UserRepository users;
	private final NotificationSender sender;
	private final BoardProperties board;
	private final Clock clock;

	public OverdueTaskNotifier(TaskRepository tasks, UserRepository users, NotificationSender sender, BoardProperties board,
			Clock clock) {
		this.tasks = tasks;
		this.users = users;
		this.sender = sender;
		this.board = board;
		this.clock = clock;
	}

	/** @return how many overdue tasks were notified */
	@Transactional
	public int notifyOverdueTasks() {
		var now = clock.instant();
		var overdue = tasks.findOverdueNotYetNotified(now);
		if (overdue.isEmpty()) {
			return 0;
		}
		var admin = boardAdmin();
		for (var task : overdue) {
			var message = "Task #%d \"%s\" is overdue: it was due %s".formatted(task.getId(), task.getTitle(), task.getDueDate());
			recipients(task, admin).forEach(recipient -> sender.send(task, recipient, message));
			task.markOverdueNotified(now);
		}
		return overdue.size();
	}

	/** The board admin and the owner, each once (the admin may own the task); an unassigned task only has the admin. */
	static List<AppUser> recipients(Task task, Optional<AppUser> admin) {
		var recipients = new ArrayList<AppUser>();
		admin.ifPresent(recipients::add);
		var owner = task.getOwner();
		if (owner != null && recipients.stream().noneMatch(r -> Objects.equals(r.getId(), owner.getId()))) {
			recipients.add(owner);
		}
		return recipients;
	}

	private Optional<AppUser> boardAdmin() {
		var admin = users.findByUsernameIgnoreCase(board.adminUsername());
		if (admin.isEmpty()) {
			log.warn("Board admin '{}' doesn't exist: overdue tasks are notified to their owners only", board.adminUsername());
		}
		return admin;
	}
}
