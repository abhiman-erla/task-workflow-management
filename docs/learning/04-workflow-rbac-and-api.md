# 04 — Projects & tasks API, workflow state machine, RBAC, pagination

## What we built
- `TaskWorkflow`: the status lifecycle as a transition table (written test-first).
- `TaskPermissions`: who may edit, assign, move, or delete a task.
- `ProjectService/Controller`, `TaskService/Controller`, DTO records, a status-change audit trail.
- Filtering + pagination on `GET /api/tasks`, with the page size capped at 100.
- Unit tests (Mockito) and an end-to-end `TaskFlowApiIT` that drives the whole flow over HTTP.

## Request flow (layers)

```
HTTP JSON ─► Controller ─► Service ─► Repository ─► Postgres
            @Valid DTO     rules +     Spring Data
            (400)          @Transactional
                           403 / 404 / 409
```
- **Controller**: HTTP only. Parse, validate (`@Valid`), choose the status code. No business logic.
- **Service**: business rules and transactions. Returns DTOs so lazy JPA data is read inside the transaction.
- **Repository**: data access only.

## Key ideas

**State machine as data.** `Map<TaskStatus, Set<TaskStatus>>` holds every legal move. It's readable, exhaustively
testable (parameterized tests cover each pair), and changing the workflow means editing one table. The API also returns
`allowedNextStatuses`, so a UI can show only valid buttons.

**Check order matters: 403 before 409.** `transition()` checks *permission* first, then the *workflow*. Otherwise a user who
can't touch a task could probe its state through error messages. (The integration test caught a wrong assumption about this. That's the point of end-to-end tests.)

**Separation of duties.** A member can push their own task to `IN_REVIEW` but can't approve it to `DONE`. That's a real business rule, enforced in `TaskPermissions` and unit-tested.

**DTOs, not entities, at the API boundary.** Entities have lazy proxies, password hashes, and bidirectional links. Returning
them causes leaks, `LazyInitializationException`, or infinite JSON. Records make DTOs one line each.

**`@Transactional` + dirty checking.** Inside a transaction, changing a managed entity (`task.setTitle(...)`) is enough.
Hibernate detects the change and issues the UPDATE at commit. We call `flush()` only so `version`/`updatedAt` in the response are current.

**`getReferenceById` vs `findById`.** `getReferenceById` returns a proxy with just the id (no SELECT). Perfect when you only
need a foreign key (`changedBy` in history). `findById` hits the DB, which you need when the row must exist (404 otherwise).

**Pagination.** `Pageable` comes from `?page=0&size=20&sort=priority,desc`. `@PageableDefault` sets defaults,
`max-page-size: 100` stops `size=1000000`. An unknown sort field becomes a 400, not a 500.

**Mocks vs real objects in unit tests.** `TaskServiceTest` mocks repositories (slow, external) but uses the *real*
`TaskWorkflow` and `TaskPermissions` (pure logic), so tests verify behaviour, not just "method X was called".

**Unit tests vs integration tests.** Unit tests (`*Test`, surefire) are fast and pinpoint a broken rule.
`*IT` (failsafe, `mvn verify`) proves the pieces fit: JSON, security filters, transactions, SQL.

## Try it
Use Swagger UI at http://localhost:8080/swagger-ui.html, or follow the curl walkthrough in the README.

## Interview questions
1. Why return 409 for an illegal status transition and not 400? *(The request is well-formed; it conflicts with the resource's current state.)*
2. Your `PUT /tasks/{id}` and someone else's both read version 3. What happens? *(One wins; the other gets 409 from `@Version`. The client must re-fetch.)*
3. How would you make this support per-project custom workflows? *(Store transitions in a table keyed by project; `TaskWorkflow` loads them.)*
4. What's the difference between `@PreAuthorize` and checking permissions in service code? When do you need each?
