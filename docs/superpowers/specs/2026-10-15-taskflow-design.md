# TaskFlow — Task & Workflow Management System: Design

**Date:** 2026-10-15 · **Status:** approved defaults (owner asked to proceed without further Q&A)

## Purpose

A backend-only REST API for managing tasks inside projects, with a fixed status workflow,
JWT authentication and role-based authorization. It is a portfolio project: every line on the
resume must be true and explainable in an interview, and the owner learns the stack while building it.

**Success criteria**
- Matches the resume description: Java + Spring Boot, REST APIs for create/update/assign/status tracking,
  JWT auth + RBAC, PostgreSQL with schemas/constraints/indexes, validation, consistent errors, pagination,
  unit tests for service and repository layers.
- `./mvnw verify` passes locally and in GitHub Actions.
- A new developer can run it with `docker compose up -d` + `./mvnw spring-boot:run`.

## Stack

| Concern | Choice | Why |
|---|---|---|
| Language/runtime | Java 21 | LTS, records, pattern matching |
| Framework | Spring Boot 3.5.x (Web, Data JPA, Security, Validation, Actuator) | industry standard |
| DB | PostgreSQL 16 (Docker Compose) | matches resume |
| Migrations | Flyway | schema is versioned SQL, not Hibernate auto-DDL |
| Auth | JWT (jjwt), BCrypt passwords, stateless sessions | matches resume |
| API docs | springdoc-openapi (Swagger UI) | easy demo |
| Tests | JUnit 5, Mockito, AssertJ, Testcontainers (real Postgres) | repository tests run against the real DB engine |
| Build/CI | Maven wrapper, GitHub Actions | |

## Domain

**User**: id, fullName, email (unique), passwordHash, role (`ADMIN` | `MANAGER` | `MEMBER`), createdAt.
Self-registration always creates `MEMBER`. An initial admin is created on startup from env vars if none exists.

**Project**: id, name (unique), description, owner (User), createdAt, updatedAt.

**Task**: id, project, title, description, status, priority (`LOW` | `MEDIUM` | `HIGH` | `URGENT`),
dueDate, assignee (nullable), createdBy, createdAt, updatedAt, version (optimistic lock).

**TaskStatusHistory**: id, task, fromStatus, toStatus, changedBy, changedAt — an audit trail of every transition.

### Workflow (state machine)

```
TODO ──► IN_PROGRESS ──► IN_REVIEW ──► DONE
  ▲          │  ▲             │
  └──────────┘  └─────────────┘   (send back)
TODO / IN_PROGRESS / IN_REVIEW ──► CANCELLED
```
- `DONE` and `CANCELLED` are terminal.
- A task must have an assignee to enter `IN_PROGRESS`.
- Any illegal transition → `409 Conflict` with a message listing the allowed next states.

### Authorization rules

| Action | ADMIN | MANAGER | MEMBER |
|---|---|---|---|
| Create / update / delete project | ✓ | ✓ (update/delete only own) | ✗ |
| View projects and tasks | ✓ | ✓ | ✓ |
| Create task in a project | ✓ | ✓ | ✓ |
| Edit task fields | ✓ | ✓ | only if creator or assignee |
| Assign / reassign task | ✓ | ✓ | ✗ |
| Status transition | ✓ | ✓ | only if assignee, and never `IN_REVIEW → DONE` (reviewer approval) |
| Delete task | ✓ | ✓ | ✗ |
| Change a user's role | ✓ | ✗ | ✗ |

Coarse rules (role only) are enforced with `@PreAuthorize`; ownership rules live in a small
`TaskPermissions` component so they are unit-testable.

## API

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/register` | → 201 user |
| POST | `/api/auth/login` | → `{accessToken, tokenType, expiresInSeconds}` |
| GET | `/api/users/me` | |
| PATCH | `/api/users/{id}/role` | ADMIN |
| POST/GET | `/api/projects` | GET is paginated |
| GET/PUT/DELETE | `/api/projects/{id}` | |
| POST | `/api/projects/{projectId}/tasks` | |
| GET | `/api/tasks` | filters: `projectId, status, assigneeId, priority`; `page, size (≤100), sort` |
| GET/PUT/DELETE | `/api/tasks/{id}` | |
| PATCH | `/api/tasks/{id}/assignee` | `{assigneeId}` (null unassigns) |
| POST | `/api/tasks/{id}/transitions` | `{status}` |
| GET | `/api/tasks/{id}/history` | |

Paginated responses use a stable DTO: `{content, page, size, totalElements, totalPages}`.

## Errors

All errors are RFC 7807 `ProblemDetail` JSON from one `@RestControllerAdvice`:
400 validation (with per-field `errors` map) · 401 missing/invalid token · 403 forbidden ·
404 not found · 409 duplicate email/project name, illegal transition, optimistic-lock conflict.

## Database

Flyway `V1__init.sql`: FKs, `NOT NULL`, `CHECK` constraints on enum columns, unique on
`users.email` and `projects.name`, indexes on `tasks(project_id)`, `tasks(assignee_id)`,
`tasks(status)`, `task_status_history(task_id)`. `spring.jpa.hibernate.ddl-auto=validate`.

## Layout

```
com.abhiman.taskflow
├── auth        (AuthController, AuthService, JwtService, JwtAuthFilter, SecurityConfig)
├── user        (User, Role, UserRepository, UserController, UserService, AdminBootstrap)
├── project     (Project, ProjectRepository, ProjectController, ProjectService, dto)
├── task        (Task, TaskStatus, Priority, TaskWorkflow, TaskPermissions, TaskStatusHistory,
│                repositories, TaskSpecifications, TaskController, TaskService, dto)
└── common      (GlobalExceptionHandler, exceptions, PageResponse, CurrentUser)
```
Package-by-feature: each folder is one concept you can read on its own.

## Testing

- `TaskWorkflowTest` — every allowed / forbidden transition (pure unit test, no Spring).
- `TaskPermissionsTest`, `TaskServiceTest`, `ProjectServiceTest`, `JwtServiceTest` — Mockito unit tests.
- `*RepositoryTest` — `@DataJpaTest` + Testcontainers Postgres (constraints, filtering specs, pagination).
- `TaskFlowApiIT` — full HTTP flow via MockMvc: register → login → create project → task → assign → transition → 403/409 cases.

## Milestones (one commit each, each with a learning note in `docs/learning/`)

1. Scaffold, Docker Compose Postgres, Flyway schema, health endpoint
2. Entities + repositories + repository tests
3. JWT auth + security config + admin bootstrap
4. Projects & tasks API, workflow, RBAC, error handling, pagination, tests
5. README, OpenAPI, GitHub Actions CI, push

## Out of scope (YAGNI)

Frontend, per-project membership, configurable workflows, refresh tokens, comments/attachments, email notifications.
