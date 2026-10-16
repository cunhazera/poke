package com.example.tasks.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.example.tasks.support.ApiTest;

/** The Flyway schema itself: column types, defaults and constraints, each proven with real SQL. */
class SchemaTest extends ApiTest {

	private static final String INSERT = "INSERT INTO task (title, description, due_date) VALUES (?, ?, now() + interval '1 day') RETURNING id";

	private Map<String, String> columnTypes(String table) {
		var types = new java.util.HashMap<String, String>();
		db.query("SELECT column_name, data_type, udt_name FROM information_schema.columns WHERE table_name = ?",
				rs -> {
					types.put(rs.getString(1), "USER-DEFINED".equals(rs.getString(2)) ? rs.getString(3) : rs.getString(2));
				}, table);
		return types;
	}

	@Test
	void createsFiveDefaultUsers() {
		assertThat(db.queryForList("SELECT username FROM app_user ORDER BY id", String.class))
				.containsExactly("ada", "alan", "grace", "linus", "margaret");
	}

	@Test
	void columnTypesMatchTheSpecification() {
		assertThat(columnTypes("task")).containsEntry("id", "bigint").containsEntry("title", "text")
				.containsEntry("description", "text").containsEntry("status", "task_status")
				.containsEntry("due_date", "timestamp with time zone").containsEntry("owner_id", "bigint");
		assertThat(columnTypes("app_user")).containsEntry("id", "bigint");
		assertThat(columnTypes("task_history")).containsEntry("action", "task_action").containsEntry("occurred_at", "timestamp with time zone");
		assertThat(db.queryForList("SELECT unnest(enum_range(NULL::task_status))::text", String.class))
				.containsExactly("TODO", "IN_PROGRESS", "DONE");
	}

	@Test
	void aNewTaskIsInTodoWithoutAnOwnerAndAGeneratedId() {
		var id = db.queryForObject(INSERT, Long.class, "Write", null);

		var row = db.queryForMap("SELECT status::text AS status, owner_id, description FROM task WHERE id = ?", id);
		assertThat(id).isPositive();
		assertThat(row).containsEntry("status", "TODO").containsEntry("owner_id", null).containsEntry("description", null);
	}

	@Test
	void theTitleIsRequiredAndAtMost124Characters() {
		assertThat(db.queryForObject(INSERT, Long.class, "x".repeat(124), null)).isPositive();
		assertThat(db.queryForObject(INSERT, Long.class, "🚀".repeat(124), null)).as("characters, not bytes").isPositive();
		for (var bad : List.of("x".repeat(125), "   ")) {
			assertThatThrownBy(() -> db.queryForObject(INSERT, Long.class, bad, null)).isInstanceOf(DataIntegrityViolationException.class);
		}
		assertThatThrownBy(() -> db.update("INSERT INTO task (title, due_date) VALUES (NULL, now())"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void theDescriptionHasNoLimit() {
		var id = db.queryForObject(INSERT, Long.class, "t", "d".repeat(1_000_000));

		assertThat(db.queryForObject("SELECT length(description) FROM task WHERE id = ?", Integer.class, id)).isEqualTo(1_000_000);
	}

	@Test
	void theDueDateIsRequired() {
		assertThatThrownBy(() -> db.update("INSERT INTO task (title) VALUES ('t')")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void onlyTheThreeStatusesExist() {
		assertThatThrownBy(() -> db.update("INSERT INTO task (title, due_date, status) VALUES ('t', now(), 'ARCHIVED')"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void aStartedTaskAlwaysHasAnOwner() {
		assertThatThrownBy(() -> db.update("INSERT INTO task (title, due_date, status) VALUES ('t', now(), 'IN_PROGRESS')"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(db.update("INSERT INTO task (title, due_date, status, owner_id) VALUES ('t', now(), 'DONE', 1)")).isEqualTo(1);
	}

	@Test
	void anOwnerMustBeAnExistingUser() {
		assertThatThrownBy(() -> db.update("INSERT INTO task (title, due_date, owner_id) VALUES ('t', now(), 999)"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingATaskDeletesItsHistoryAndNotifications() {
		var id = db.queryForObject(INSERT, Long.class, "t", null);
		db.update("INSERT INTO task_history (task_id, action) VALUES (?, 'CREATED')", id);
		db.update("INSERT INTO notification (task_id, recipient_id, message) VALUES (?, 1, 'm')", id);

		db.update("DELETE FROM task WHERE id = ?", id);

		assertThat(db.queryForObject("SELECT count(*) FROM task_history", Integer.class)).isZero();
		assertThat(db.queryForObject("SELECT count(*) FROM notification", Integer.class)).isZero();
	}

	@Test
	void aStatusChangeRecordsBothStatuses() {
		var id = db.queryForObject(INSERT, Long.class, "t", null);

		assertThatThrownBy(() -> db.update("INSERT INTO task_history (task_id, action, to_status) VALUES (?, 'STATUS_CHANGED', 'DONE')", id))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void usernamesAndEmailsAreUniqueInAnyCase() {
		assertThatThrownBy(() -> db.update("INSERT INTO app_user (full_name, username, email) VALUES ('X', 'ADA', 'x@example.com')"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> db.update("INSERT INTO app_user (full_name, username, email) VALUES ('X', 'x', 'ADA@example.com')"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
