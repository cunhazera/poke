package com.example.tasks.task;

import java.time.Instant;

import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

import com.example.tasks.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One action on a task: what changed (status and/or owner, from and to) and when. Written only by {@link Task}. */
@Entity
@Table(name = "task_history")
public class TaskHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id")
	private Task task;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(nullable = false, columnDefinition = "task_action")
	private TaskAction action;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(name = "from_status", columnDefinition = "task_status")
	private TaskStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@JdbcType(PostgreSQLEnumJdbcType.class)
	@Column(name = "to_status", columnDefinition = "task_status")
	private TaskStatus toStatus;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "from_owner_id")
	private AppUser fromOwner;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "to_owner_id")
	private AppUser toOwner;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected TaskHistory() {
	}

	TaskHistory(Task task, TaskAction action, TaskStatus fromStatus, TaskStatus toStatus, AppUser fromOwner,
			AppUser toOwner, Instant occurredAt) {
		this.task = task;
		this.action = action;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.fromOwner = fromOwner;
		this.toOwner = toOwner;
		this.occurredAt = occurredAt;
	}

	public Long getId() {
		return id;
	}

	public TaskAction getAction() {
		return action;
	}

	public TaskStatus getFromStatus() {
		return fromStatus;
	}

	public TaskStatus getToStatus() {
		return toStatus;
	}

	public AppUser getFromOwner() {
		return fromOwner;
	}

	public AppUser getToOwner() {
		return toOwner;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}
