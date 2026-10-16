package com.example.tasks.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The basic user model: users already exist (five are created by Flyway) and are only read by this API. */
@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "full_name", nullable = false, columnDefinition = "text")
	private String fullName;

	@Column(nullable = false, columnDefinition = "text")
	private String username;

	@Column(nullable = false, columnDefinition = "text")
	private String email;

	protected AppUser() {
	}

	public AppUser(Long id, String fullName, String username, String email) {
		this.id = id;
		this.fullName = fullName;
		this.username = username;
		this.email = email;
	}

	public Long getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public String getUsername() {
		return username;
	}

	public String getEmail() {
		return email;
	}
}
