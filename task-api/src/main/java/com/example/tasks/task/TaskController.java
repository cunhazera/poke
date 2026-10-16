package com.example.tasks.task;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.tasks.web.PageResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/** Tasks: CRUD, plus their status (moving on the board), owner (assignment) and history. */
@RestController
@RequestMapping("/api/tasks")
class TaskController {

	private final TaskService service;

	TaskController(TaskService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<TaskResponse> create(@RequestBody TaskRequest request) {
		var task = service.create(request.title(), request.description(), instant(request.dueDate()));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(task.getId()).toUri();
		return ResponseEntity.created(location).body(respond(task));
	}

	@GetMapping
	PageResponse<TaskResponse> list(
			@RequestParam(required = false) TaskStatus status,
			@RequestParam(required = false) @Positive Long ownerId,
			@RequestParam(required = false) Boolean unassigned,
			@RequestParam(required = false) Boolean overdue,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		var filter = new TaskFilter(status, ownerId, unassigned, overdue);
		var now = service.now();
		return PageResponse.of(service.list(filter, PageRequest.of(page, size)), task -> TaskResponse.of(task, now));
	}

	@GetMapping("/{id}")
	TaskResponse get(@PathVariable @Positive long id) {
		return respond(service.get(id));
	}

	/** Replaces the title, description and due date (all three are sent; an omitted description is cleared). */
	@PutMapping("/{id}")
	TaskResponse update(@PathVariable @Positive long id, @RequestBody TaskRequest request) {
		return respond(service.updateDetails(id, request.title(), request.description(), instant(request.dueDate())));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
	void delete(@PathVariable @Positive long id) {
		service.delete(id);
	}

	/** Moves the task to the neighbouring column (Todo ⇄ In progress ⇄ Done). */
	@PutMapping("/{id}/status")
	TaskResponse move(@PathVariable @Positive long id, @RequestBody StatusRequest request) {
		return respond(service.moveTo(id, request.status()));
	}

	/** Assigns the task to a user, replacing the current owner. */
	@PutMapping("/{id}/owner")
	TaskResponse assign(@PathVariable @Positive long id, @RequestBody OwnerRequest request) {
		return respond(service.assign(id, request.userId()));
	}

	/** Leaves a task in To do without an owner. */
	@DeleteMapping("/{id}/owner")
	TaskResponse unassign(@PathVariable @Positive long id) {
		return respond(service.unassign(id));
	}

	@GetMapping("/{id}/history")
	List<HistoryResponse> history(@PathVariable @Positive long id) {
		return service.history(id).stream().map(HistoryResponse::of).toList();
	}

	private TaskResponse respond(Task task) {
		return TaskResponse.of(task, service.now());
	}

	private static java.time.Instant instant(OffsetDateTime dateTime) {
		return dateTime == null ? null : dateTime.toInstant();
	}
}
