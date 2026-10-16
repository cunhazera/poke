package com.example.tasks.notification;

import java.time.Instant;

import com.example.tasks.task.Task;
import com.example.tasks.user.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A notification "sent" by the mock channel: recorded instead of e-mailed, so it can be inspected. */
@Entity
@Table(name = "notification")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id")
	private Task task;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recipient_id")
	private AppUser recipient;

	@Column(nullable = false, columnDefinition = "text")
	private String message;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	protected Notification() {
	}

	public Notification(Task task, AppUser recipient, String message, Instant sentAt) {
		this.task = task;
		this.recipient = recipient;
		this.message = message;
		this.sentAt = sentAt;
	}

	public Long getId() {
		return id;
	}

	public Task getTask() {
		return task;
	}

	public AppUser getRecipient() {
		return recipient;
	}

	public String getMessage() {
		return message;
	}

	public Instant getSentAt() {
		return sentAt;
	}
}
