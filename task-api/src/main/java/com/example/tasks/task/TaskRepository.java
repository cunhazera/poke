package com.example.tasks.task;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every list loads the owner in the same query (no N+1 when responses show it). */
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

	@Override
	@EntityGraph(attributePaths = "owner")
	Optional<Task> findById(Long id);

	@Override
	@EntityGraph(attributePaths = "owner")
	Page<Task> findAll(Specification<Task> spec, Pageable pageable);

	@Override
	@EntityGraph(attributePaths = "owner")
	List<Task> findAll(Specification<Task> spec, Sort sort);

	/**
	 * Open tasks whose due date has been reached and whose admin and owner haven't been told yet. The status is a
	 * parameter, not a JPQL literal: a literal makes Hibernate cast to a type named after the Java enum
	 * ({@code taskstatus}), which isn't the column's type ({@code task_status}).
	 */
	@EntityGraph(attributePaths = "owner")
	@Query("""
			select t from Task t
			where t.status <> :done
			  and t.dueDate <= :now
			  and t.overdueNotifiedAt is null
			order by t.dueDate, t.id
			""")
	List<Task> findOverdue(@Param("done") TaskStatus done, @Param("now") Instant now);

	default List<Task> findOverdueNotYetNotified(Instant now) {
		return findOverdue(TaskStatus.DONE, now);
	}
}
