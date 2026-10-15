# TaskFlow: Task & Workflow Management API

[![CI](https://github.com/abhiman-06/task-workflow-management/actions/workflows/ci.yml/badge.svg)](https://github.com/abhiman-06/task-workflow-management/actions/workflows/ci.yml)

A backend REST API for managing tasks inside projects, built with **Java 21, Spring Boot 3, PostgreSQL and JWT**.
Tasks follow an enforced workflow (`TODO → IN_PROGRESS → IN_REVIEW → DONE`), access is controlled by roles and
ownership, and every status change is recorded in an audit trail.

## Features

- **JWT authentication**: register/login, stateless bearer tokens, BCrypt password hashing
- **Role-based authorization** (`ADMIN`, `MANAGER`, `MEMBER`) plus ownership rules (assignee / creator / project owner)
- **Workflow state machine**: illegal transitions are rejected with `409` and the list of allowed next states. Members can't approve their own work.
- **Task management**: create, update, assign, transition, delete, plus a status-change history per task
- **Filtering & pagination**: `GET /api/tasks?projectId=&status=&assigneeId=&priority=&page=&size=&sort=`
- **Consistent errors**: RFC 7807 `application/problem+json` for every failure, with per-field validation messages
- **PostgreSQL schema via Flyway**: foreign keys, `CHECK`/`UNIQUE` constraints, indexes on filter columns
- **Optimistic locking**: concurrent edits return `409` instead of silently overwriting
- **Tests**: unit tests (JUnit 5, Mockito) and repository + end-to-end tests against real Postgres (Testcontainers)
- **OpenAPI / Swagger UI** and **GitHub Actions CI**

## Architecture

```
com.abhiman.taskflow
├── auth      JWT issue/verify, security filter chain, register/login
├── user      users, roles, admin bootstrap
├── project   project CRUD
├── task      tasks, TaskWorkflow (state machine), TaskPermissions, history, filtering
└── common    error handling, pagination DTO, OpenAPI config
```

Controller (HTTP + validation) → Service (business rules, transactions, returns DTOs) → Spring Data repository → PostgreSQL.

### Task workflow

```
TODO ──► IN_PROGRESS ──► IN_REVIEW ──► DONE
  ▲          │  ▲             │
  └──────────┘  └─────────────┘   (send back)
TODO / IN_PROGRESS / IN_REVIEW ──► CANCELLED
```

### Permissions

| Action | ADMIN | MANAGER | MEMBER |
|---|---|---|---|
| Create project | ✓ | ✓ | ✗ |
| Update / delete project | ✓ | own projects | ✗ |
| Create task | ✓ | ✓ | ✓ |
| Edit task | ✓ | ✓ | if creator or assignee |
| Assign / delete task | ✓ | ✓ | ✗ |
| Change status | ✓ | ✓ | if assignee (not `IN_REVIEW → DONE`) |
| Change user role | ✓ | ✗ | ✗ |

## Running locally

Requirements: Java 21, Docker.

```bash
docker compose up -d          # PostgreSQL 16 on localhost:5432
./mvnw spring-boot:run        # API on http://localhost:8080
```

Swagger UI: http://localhost:8080/swagger-ui.html

A bootstrap admin is created on first start (`admin@taskflow.local` / `Admin@12345`). **Override in any real environment:**

| Env var | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | database | local Docker values |
| `JWT_SECRET` | Base64 HMAC key (≥ 256 bits) | dev-only key |
| `JWT_EXPIRATION` | token lifetime (ISO-8601) | `PT1H` |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | bootstrap admin | see above |

### Quick walkthrough

```bash
# log in as admin
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@taskflow.local","password":"Admin@12345"}' | jq -r .accessToken)

# create a project and a task
curl -s -X POST localhost:8080/api/projects -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Website relaunch"}'
curl -s -X POST localhost:8080/api/projects/1/tasks -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"title":"Design schema","priority":"HIGH","assigneeId":1}'

# move it through the workflow
curl -s -X POST localhost:8080/api/tasks/1/transitions -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"status":"IN_PROGRESS"}'

# filter + paginate
curl -s "localhost:8080/api/tasks?status=IN_PROGRESS&page=0&size=10&sort=dueDate,asc" -H "Authorization: Bearer $TOKEN"
```

## API

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Register (role `MEMBER`) |
| POST | `/api/auth/login` | Get a JWT |
| GET | `/api/users/me` | Current user |
| PATCH | `/api/users/{id}/role` | Change role (admin) |
| POST, GET | `/api/projects` | Create / list (paginated) |
| GET, PUT, DELETE | `/api/projects/{id}` | Read / update / delete |
| POST | `/api/projects/{projectId}/tasks` | Create task |
| GET | `/api/tasks` | Search tasks (filters + pagination) |
| GET, PUT, DELETE | `/api/tasks/{id}` | Read / update / delete |
| PATCH | `/api/tasks/{id}/assignee` | Assign or unassign |
| POST | `/api/tasks/{id}/transitions` | Change status |
| GET | `/api/tasks/{id}/history` | Status audit trail |

## Testing

```bash
./mvnw test     # unit + repository tests
./mvnw verify   # + end-to-end API tests (needs Docker for Testcontainers)
```

## Known limitations / next steps

- Sorting by `priority` or `status` is alphabetical, because enums are stored as strings. A numeric rank column would fix it.
- No refresh tokens or token revocation. Role changes apply on next login.
- Projects have no membership list: every authenticated user can see every project.

## Learning notes

This project was built in milestones, each with notes on the concepts behind it: [`docs/learning`](docs/learning).
