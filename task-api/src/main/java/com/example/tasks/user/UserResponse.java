package com.example.tasks.user;

public record UserResponse(long id, String fullName, String username, String email) {

	static UserResponse of(AppUser user) {
		return new UserResponse(user.getId(), user.getFullName(), user.getUsername(), user.getEmail());
	}
}
