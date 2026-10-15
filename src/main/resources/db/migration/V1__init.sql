-- Initial schema for TaskFlow.
-- Enum-like columns are VARCHAR + CHECK so values stay readable in SQL and Java enums map with @Enumerated(STRING).

CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'MANAGER', 'MEMBER'))
);

CREATE TABLE projects (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(2000),
    owner_id    BIGINT       NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_projects_name UNIQUE (name)
);

CREATE INDEX idx_projects_owner ON projects (owner_id);

CREATE TABLE tasks (
    id            BIGSERIAL    PRIMARY KEY,
    project_id    BIGINT       NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    title         VARCHAR(200) NOT NULL,
    description   VARCHAR(5000),
    status        VARCHAR(20)  NOT NULL,
    priority      VARCHAR(10)  NOT NULL,
    due_date      DATE,
    assignee_id   BIGINT       REFERENCES users (id),
    created_by_id BIGINT       NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

-- Indexes match the filters exposed by GET /api/tasks.
CREATE INDEX idx_tasks_project ON tasks (project_id);
CREATE INDEX idx_tasks_assignee ON tasks (assignee_id);
CREATE INDEX idx_tasks_status ON tasks (status);

CREATE TABLE task_status_history (
    id            BIGSERIAL   PRIMARY KEY,
    task_id       BIGINT      NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    from_status   VARCHAR(20) NOT NULL,
    to_status     VARCHAR(20) NOT NULL,
    changed_by_id BIGINT      NOT NULL REFERENCES users (id),
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_history_task ON task_status_history (task_id);
