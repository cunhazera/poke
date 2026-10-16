package com.app.poke.domain.model;

/** What a user may do. Registration always grants {@link #USER}; administrators are provisioned, never self-registered. */
public enum Role {
	USER,
	ADMIN
}
