package com.app.poke.adapter.in.web.dto;

/** A user as the API shows it: never the password or its hash. */
public record UserResponse(long id, String username, String email, String role) {
}
