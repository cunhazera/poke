package com.example.tasks.user;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.tasks.error.NotFoundException;

import jakarta.validation.constraints.Positive;

/** Users already exist (the basic user model): this API only reads them, to find who to assign tasks to. */
@RestController
@RequestMapping("/api/users")
class UserController {

	private final UserRepository users;

	UserController(UserRepository users) {
		this.users = users;
	}

	@GetMapping
	List<UserResponse> list() {
		return users.findAll(Sort.by("id")).stream().map(UserResponse::of).toList();
	}

	@GetMapping("/{id}")
	UserResponse get(@PathVariable @Positive long id) {
		return users.findById(id).map(UserResponse::of).orElseThrow(() -> new NotFoundException("User", id));
	}
}
