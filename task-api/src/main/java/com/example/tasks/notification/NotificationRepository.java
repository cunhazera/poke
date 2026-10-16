package com.example.tasks.notification;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	@EntityGraph(attributePaths = { "task", "recipient" })
	List<Notification> findAllByOrderBySentAtDescIdDesc();

	@EntityGraph(attributePaths = { "task", "recipient" })
	List<Notification> findByRecipientIdOrderBySentAtDescIdDesc(Long recipientId);
}
