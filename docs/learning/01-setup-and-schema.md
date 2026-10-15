# 01 — Project setup, Docker Postgres, Flyway schema

## What we built
- A Maven + Spring Boot 3.5 project (`pom.xml`, Maven wrapper `mvnw`).
- `docker-compose.yml` that runs PostgreSQL 16 locally.
- `V1__init.sql`, the first Flyway migration, which creates `users`, `projects`, `tasks`, `task_status_history`.
- `application.yml` with config read from environment variables (`${DB_URL:default}`).

## Key ideas

**Spring Boot starters.** `spring-boot-starter-web` pulls in Spring MVC + embedded Tomcat + Jackson.
`starter-data-jpa` pulls in Hibernate + Spring Data. You add one dependency per capability.

**Why Flyway instead of `ddl-auto=update`?** Hibernate's auto-update guesses schema changes and
can't do data migrations or drops safely. Flyway runs versioned SQL files (`V1__`, `V2__`…) exactly
once each and records them in `flyway_schema_history`. Schema changes become reviewable code.
We set `ddl-auto: validate` so Hibernate *checks* the entities match the tables, and the app fails fast if they don't.

**Constraints belong in the database.** Validation in Java can be bypassed (another service, a bug,
a manual SQL fix). `NOT NULL`, `UNIQUE`, `FOREIGN KEY` and `CHECK` make bad data impossible.

**Indexes match queries.** We'll filter tasks by project, assignee and status, so each has an index.
Without them Postgres scans the whole table. Trade-off: every index slows inserts a little and uses disk.

**`open-in-view: false`.** By default Spring keeps the DB session open during JSON rendering, which hides
lazy-loading bugs and holds connections longer. Turning it off forces us to load what we need in the service layer.

**12-factor config.** `${DB_PASSWORD:taskflow}` means "env var `DB_PASSWORD`, else `taskflow`". The same jar runs
everywhere; only env vars change.

## Try it
```bash
docker compose up -d
./mvnw spring-boot:run
curl localhost:8080/actuator/health
docker exec -it taskflow-postgres psql -U taskflow -c '\d tasks'
```

## Interview questions
1. What happens if you edit `V1__init.sql` after it has been applied? *(Flyway checksum mismatch, so startup fails. Always add a new `V2__` file instead.)*
2. Why `VARCHAR + CHECK` instead of a Postgres `ENUM` type? *(Adding a value to a PG enum needs `ALTER TYPE`, and it maps less cleanly with JPA. CHECK is easy to change in a migration.)*
3. What does `ON DELETE CASCADE` on `tasks.project_id` do, and when would it be dangerous?
