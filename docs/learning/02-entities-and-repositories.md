# 02 — JPA entities, repositories, and repository tests

## What we built
- Entities: `User`, `Project`, `Task`, `TaskStatusHistory` + enums `Role`, `TaskStatus`, `Priority`.
- Spring Data repositories, including dynamic filtering with `Specification`s (`TaskSpecifications`).
- `@DataJpaTest` tests that run against a **real Postgres** started by Testcontainers.

## Key ideas

**Entity = Java view of a table.** `@Entity` + `@Table(name = "tasks")`, `@Id` + `IDENTITY` uses the `BIGSERIAL`
column. `@Enumerated(EnumType.STRING)` stores `"IN_PROGRESS"`, not `1`. Storing ordinals breaks silently if someone reorders the enum.

**`protected` no-arg constructor.** JPA needs it to create objects via reflection. Making it `protected` stops
application code from creating half-built entities; real code uses the constructor with required fields.

**`FetchType.LAZY` on every `@ManyToOne`.** The default for `@ManyToOne` is EAGER, which loads the related row every
time, even when unused. LAZY loads it on first access. The catch is the **N+1 problem**: listing 20 tasks and then reading
`task.getAssignee().getName()` fires 1 + 20 queries. `@EntityGraph(attributePaths = {...})` on the repository method
fetches those associations with a JOIN in a single query.

**Derived queries.** `findByEmailIgnoreCase`, `existsByRole`. Spring parses the method name and writes the JPQL.
Good for simple lookups. Use `@Query` or Specifications when it gets complex.

**Specifications = composable WHERE clauses.** Each filter is a tiny predicate. `Specification.allOf(...)` ANDs them and
skips the `null` ones, so an optional filter just returns `null`. This avoids writing 2⁴ = 16 repository methods for 4 optional filters.

**`@Version` optimistic locking.** Two users load version 3 of a task. The first save writes version 4. The second save runs
`UPDATE … WHERE id=? AND version=3`, which matches 0 rows, so Hibernate throws `ObjectOptimisticLockingFailureException`. We'll map it to HTTP 409.

**Package-private `setStatus`.** Only code in the `task` package can change status. Later, the workflow rules will be the
only path to change it. Encapsulation enforced by the compiler.

**Why Testcontainers instead of H2?** H2 is a different database: different SQL dialect, different constraint behaviour,
no `TIMESTAMPTZ`. Tests passing on H2 can fail on Postgres. Testcontainers runs the same Postgres 16 image as production.
`@ServiceConnection` wires the container's URL into Spring automatically.

**`@DataJpaTest`** loads only JPA beans (no web, no security), so it's fast. Each test runs in a transaction that is
**rolled back** afterwards, so tests don't see each other's data.

## Try it
```bash
./mvnw test -Dtest=TaskRepositoryTest
```

## Interview questions
1. Explain the N+1 query problem and two ways to fix it. *(JOIN FETCH / @EntityGraph, batch fetching `@BatchSize`, DTO projections.)*
2. Optimistic vs pessimistic locking: when would you use `SELECT … FOR UPDATE` instead?
3. Why does `@DataJpaTest` roll back each test, and when could that hide a bug? *(Flush-time constraint errors only show up when the SQL is actually sent, which is why the duplicate-email test uses `saveAndFlush`.)*
