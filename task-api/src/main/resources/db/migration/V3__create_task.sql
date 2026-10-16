CREATE TYPE task_status AS ENUM ('TODO', 'IN_PROGRESS', 'DONE');

CREATE TABLE task (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- TEXT with a length check rather than VARCHAR(124): the rule is the check, the type stays plain text.
    title               TEXT        NOT NULL CHECK (btrim(title) <> '' AND char_length(title) <= 124),
    description         TEXT,
    status              task_status NOT NULL DEFAULT 'TODO',
    due_date            TIMESTAMPTZ NOT NULL,
    -- No owner on creation; an owner is assigned afterwards. One owner per task, many tasks per user.
    owner_id            BIGINT REFERENCES app_user (id),
    -- When the overdue warning went out (board admin + owner), so it is sent once per missed due date.
    overdue_notified_at TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A task in progress or done always has someone working on it.
    CONSTRAINT task_owner_when_started CHECK (status = 'TODO' OR owner_id IS NOT NULL)
);

CREATE INDEX ix_task_owner  ON task (owner_id);
CREATE INDEX ix_task_status ON task (status);
-- The overdue scan: open tasks by due date.
CREATE INDEX ix_task_open_due ON task (due_date) WHERE status <> 'DONE';

CREATE TYPE task_action AS ENUM ('CREATED', 'DETAILS_UPDATED', 'STATUS_CHANGED', 'OWNER_CHANGED', 'OVERDUE_NOTIFIED');

-- One row per action on a task, with what changed and when. Deleting a task deletes its history.
CREATE TABLE task_history (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id       BIGINT      NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    action        task_action NOT NULL,
    from_status   task_status,
    to_status     task_status,
    from_owner_id BIGINT REFERENCES app_user (id),
    to_owner_id   BIGINT REFERENCES app_user (id),
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT task_history_status_change CHECK (action <> 'STATUS_CHANGED' OR (from_status IS NOT NULL AND to_status IS NOT NULL))
);

CREATE INDEX ix_task_history_task ON task_history (task_id, occurred_at, id);

-- The mock notification channel: each "sent" notification is recorded here (and logged) instead of e-mailed.
CREATE TABLE notification (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id      BIGINT NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    recipient_id BIGINT NOT NULL REFERENCES app_user (id),
    message      TEXT   NOT NULL,
    sent_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_notification_recipient ON notification (recipient_id, sent_at);
