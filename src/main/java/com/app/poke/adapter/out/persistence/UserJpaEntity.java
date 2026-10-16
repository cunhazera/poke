package com.app.poke.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.app.poke.domain.model.Role;

/** The {@code app_user} row (schema owned by Flyway V2). Never leaves this package: {@link JpaUserGateway} maps it. */
@Entity
@Table(name = "app_user")
class UserJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String username;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	protected UserJpaEntity() {
	}

	UserJpaEntity(String username, String email, String passwordHash, Role role) {
		this.username = username;
		this.email = email;
		this.passwordHash = passwordHash;
		this.role = role;
	}

	Long id() {
		return id;
	}

	String username() {
		return username;
	}

	String email() {
		return email;
	}

	String passwordHash() {
		return passwordHash;
	}

	Role role() {
		return role;
	}
}
