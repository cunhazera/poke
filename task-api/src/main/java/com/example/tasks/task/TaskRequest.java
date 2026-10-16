package com.example.tasks.task;

import java.time.OffsetDateTime;

/**
 * Creates a task, or replaces its details (PUT). Status and owner aren't here: they change through their own
 * endpoints, which apply their rules. The rules for these fields live in {@link Task}, which reports every broken one.
 *
 * @param dueDate an ISO-8601 date and time with an offset, e.g. {@code 2026-10-20T18:00:00-03:00}
 */
public record TaskRequest(String title, String description, OffsetDateTime dueDate) {
}
