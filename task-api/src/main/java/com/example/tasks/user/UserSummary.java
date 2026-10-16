package com.example.tasks.user;

/** A user as shown inside other resources (a task's owner, a notification's recipient). */
public record UserSummary(long id, String username, String fullName) {

	public static UserSummary of(AppUser user) {
		return user == null ? null : new UserSummary(user.getId(), user.getUsername(), user.getFullName());
	}
}
