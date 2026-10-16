package com.example.tasks.task;

import java.util.Arrays;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/board")
class BoardController {

	private final TaskService service;

	BoardController(TaskService service) {
		this.service = service;
	}

	/** Every task, by column and due date; {@code ownerId} shows one user's tasks only. */
	@GetMapping
	BoardResponse board(@RequestParam(required = false) @Positive Long ownerId) {
		var now = service.now();
		var tasks = service.board(new TaskFilter(null, ownerId, null, null)).stream()
				.map(task -> TaskResponse.of(task, now)).toList();
		var columns = Arrays.stream(TaskStatus.values()).map(status -> {
			var inColumn = tasks.stream().filter(task -> task.status() == status).toList();
			return new BoardResponse.Column(status, status.label(), inColumn.size(), inColumn);
		}).toList();
		return new BoardResponse(now, tasks.stream().filter(TaskResponse::overdue).count(), columns);
	}
}
