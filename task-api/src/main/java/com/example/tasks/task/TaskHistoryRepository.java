package com.example.tasks.task;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskHistoryRepository extends JpaRepository<TaskHistory, Long> {

	/** Oldest first; entries of the same instant in the order they were written. */
	@EntityGraph(attributePaths = { "fromOwner", "toOwner" })
	List<TaskHistory> findByTaskIdOrderByOccurredAtAscIdAsc(Long taskId);
}
