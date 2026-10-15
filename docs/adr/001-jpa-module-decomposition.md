# ADR-001: Decomposition of tzatziki-spring-jpa into Three Layered Modules

## Status

Proposed

## Date

2026-04-28

## Context

The `tzatziki-spring-jpa` module bundled three concerns into a single artifact:

1. **Raw SQL/JDBC operations** — table cleanup, trigger management, DataSource registry
2. **JPA entity management** — CRUD via EntityManager, entity graph optimization, Hibernate serialization
3. **Spring Data integration** — CrudRepository/PagingAndSortingRepository, ApplicationContext wiring, Spring-specific annotations

Users wanting to test a pure JPA application (without Spring) or raw JDBC logic (without JPA) were forced to depend on the full Spring Boot stack. This violates the principle of minimal dependency and limits adoption.

## Decision

We decompose `tzatziki-spring-jpa` into three layered modules:

```
tzatziki-spring-jpa  (thin Spring bridge)
       │
       ▼
  tzatziki-jpa       (pure JPA, no Spring)
       │
       ▼
   tzatziki-db       (pure JDBC, no JPA)
```

### Module: `tzatziki-db`

**Purpose**: Database-level test operations using JDBC with a small internal SQL DSL.

**Key classes**:
- `DatabaseCleaner` — Truncates tables via raw SQL `Connection`/`Statement`. Supports schema-scoped cleaning and trigger management.
- `JdbcBackend` — Default `DbBackend` implementation for dynamic INSERT / SELECT / COUNT operations.
- `SqlIdentifier`, `Sql` — Identifier validation/quoting and rendering of the four statements used by the module
  (INSERT, SELECT, COUNT, TRUNCATE).
- `InsertionMode` — Enum (`DEFAULT`, `ONLY`) controlling whether to truncate before insert.
- `DatabaseSteps` — Cucumber steps: trigger enable/disable, DataSource registry, `@Before(order=100)` autoclean hook.

**Dependencies**: `tzatziki-core`, `javax.sql` (JDBC API)

**Design decisions**:

- Rewrote `JdbcTemplate`/`DataSourceTransactionManager` usage with plain JDBC. SQL rendering is centralized in a
  small `Sql` helper backed by `SqlIdentifier` validation, so `JdbcBackend` no longer assembles SQL ad hoc in each method.
- DataSource registry is static to allow cross-module registration
- Trigger management is the canonical location (only here, not duplicated in JPA layer)

### Module: `tzatziki-jpa`

**Purpose**: JPA entity testing without Spring, using `EntityManager` directly.

**Key classes**:
- `JpaBackend` — Interface defining the contract: EntityManager hooks (`getManagedEntityClasses`, `withEntityManager`, `withTransaction`) plus default implementations for entity CRUD/query/count/truncate, table-name resolution, and table-level `DbBackend` bridging
- `PlainJpaBackend` — Default implementation using `EntityManagerFactory`; mainly wires entity classes to `EntityManagerFactory`/`DataSource` and inherits the common `JpaBackend` logic
- `JpaSteps` — Cucumber steps for entity insert/assert/variable-assignment (cleaning is delegated to `DatabaseSteps`)
- `PersistenceUtil` — Jackson serializer module that skips uninitialized Hibernate proxies and `@Transient` fields
- `EntityGraphUtils` — Builds JPA entity graphs from expected field sets (only fetches asserted columns)

**Dependencies**: `tzatziki-db`, `tzatziki-jackson`, `jakarta.persistence-api` and `hibernate-core` in `provided` scope (versions managed by the Spring Boot BOM; the user's application brings its own JPA implementation)

**Design decisions**:
- **Backend Interface Pattern** (inspired by `tzatziki-kafka`'s `KafkaBackend`): The `JpaBackend` interface allows different implementations (plain JPA vs Spring Data) to be plugged in via `JpaSteps.registerBackend()`.
- **Shared EntityManager core**: `JpaBackend` owns the common save/find/query/count/truncate logic through `withEntityManager(...)` and `withTransaction(...)`. Plain JPA and Spring JPA therefore share the same EntityManager-based behavior for JPA-managed entities while each implementation still controls lifecycle and transaction boundaries.
- **Backend-owned query lifecycle**: expected-field reads stay inside `JpaBackend.findAllWithExpectedFields(...)`. Callers never receive a raw `EntityManager`, so plain JPA and Spring can each manage persistence-context lifecycle safely.
- **Shared table resolution without Spring compile deps**: `JpaBackend` resolves both `jakarta.persistence.Table` and Spring Data Relational `@Table` via reflection, so table-name lookup is centralized without pulling Spring into `tzatziki-jpa`.
- `PersistenceUtil.registerTransientAnnotation()` — extensible transient detection; the Spring layer registers `org.springframework.data.annotation.Transient` without polluting the JPA layer with Spring deps.
- `JpaSteps` injects `DatabaseSteps` via Cucumber DI and delegates trigger toggling during inserts to it (single source of truth for trigger state).

### Module: `tzatziki-spring-jpa` (refactored)

**Purpose**: Thin bridge registering Spring Data as the JPA backend.

**Key classes**:
- `SpringJpaBackend` — Implements `JpaBackend` by exposing Spring `EntityManagerFactory`/`DataSource`/`CrudRepository` lookups. Inserts go through the entity's `CrudRepository` when one exists (so Spring transaction management, JTA and custom repository logic apply, as before the split); entities without repository fall back to the shared EntityManager-based `JpaBackend` defaults. Reads, counts and truncates use the shared `JpaBackend` defaults.
- `SpringJPASteps` — Slimmed from ~380 to ~145 lines. `@Before(order=10)` hook registers `SpringJpaBackend` and propagates configuration. Retains Spring-only step definitions: CrudRepository-typed inserts, PagingAndSortingRepository sorted queries.

**Dependencies**: `tzatziki-jpa`, `tzatziki-spring`, Spring Boot starters

**Design decisions**:
- Backward-compatible `autoclean` and `schemasToClean` fields — existing test code sets `SpringJPASteps.autoclean`/`SpringJPASteps.schemasToClean`; the `@Before` hook syncs them to `DatabaseSteps`, the single owner of the cleaning configuration.
- The pre-split public Java API of `SpringJPASteps` (repository helpers, table/entity step methods) is kept as `@Deprecated(forRemoval = true)` delegates.
- Spring's `@Transient` registered via `PersistenceUtil.registerTransientAnnotation()` in the `@Before` hook.
- `CrudRepository` is used for inserts whenever available, for Spring-specific repository steps, and for entities that are not part of the JPA metamodel (for example Spring Data JDBC repositories). Queries, counts and truncates use the shared EntityManager/JDBC logic.

## Hook Ordering

| Order | Hook | Module | Purpose |
|-------|------|--------|---------|
| 10 | `SpringJPASteps.before()` | spring-jpa | Register SpringJpaBackend, propagate config |
| 100 | `DatabaseSteps.before()` | db | Autoclean of every registered DataSource (single cleaning hook for all modules) |

## Key Design Principles

1. **Backward compatibility**: All 18 existing spring-jpa test scenarios pass without modification.
2. **Minimal dependency**: Each module only pulls what it needs. `tzatziki-db` has no JPA/Hibernate; `tzatziki-jpa` has no Spring.
3. **Extensibility over inheritance**: `JpaBackend` interface + `registerBackend()` pattern allows plugging different implementations.
4. **Single responsibility**: Trigger management only in `DatabaseSteps`, entity CRUD only in `JpaSteps`, Spring wiring only in `SpringJPASteps`.
5. **Progressive disclosure**: Users pick the layer matching their stack — raw SQL, JPA, or Spring Data.

## DbBackend Interface Pattern

In addition to the `JpaBackend` interface (entity-level operations), a `DbBackend` interface handles **table-level** operations in `tzatziki-db`:

```java
public interface DbBackend {
    void insertRows(String tableName, List<Map<String, Object>> rows);
    List<Map<String, Object>> queryAll(String tableName, List<Map<String, Object>> expectedRows);
    long count(String tableName);
    void truncate(String tableName);
}
```

**Implementations**:
- `JdbcBackend` (default, in `tzatziki-db`) — Pure JDBC with metadata-driven type conversion. Queries `DatabaseMetaData.getColumns()` to resolve column SQL types and converts String values appropriately (NUMERIC, INTEGER, BIGINT, BOOLEAN, etc.).
- `JpaBackend` (in `tzatziki-jpa`) — `JpaBackend extends DbBackend` with **default methods** that bridge table-name
  operations to entity-class operations and host the common EntityManager-based entity CRUD/query/count/truncate logic.
  This means every JPA backend (PlainJpaBackend, SpringJpaBackend) is automatically a `DbBackend` — one backend per
  module, no adapter class needed. `SpringJpaBackend` overrides inserts to prefer Spring Data repositories, and handles entities that
  are outside the JPA metamodel. Tables that do not map to a managed entity (join tables, audit tables...) fall back to a
  `JdbcBackend` on the first registered `DataSource`.

**Registration flow**:
```
Spring @Before(order=10) → JpaSteps.registerBackend(springJpaBackend)
                         → DatabaseSteps.registerBackend(jpaBackend) + registerDataSource(...)  // JpaBackend IS-A DbBackend
DB  @Before(order=100)   → uses registered DbBackend (or falls back to JdbcBackend)
```

When `tzatziki-jpa` is on the classpath, table-level steps (`the X table will contain`, `the X table contains`) route
through `JpaBackend` default methods, which delegate to entity-class methods like `findAllWithExpectedFields(...)`. This
preserves relationship resolution and lazy loading initialization while keeping persistence-context ownership inside the
backend implementation. Without it, they use plain JDBC.

**Key design decision**: `queryAll()` accepts the full `expectedRows` (not just column names) so the JPA adapter can build entity graphs matching the exact structure being asserted — fetching only what's needed and initializing nested lazy associations matching the assertion depth.

## Test Coverage

| Module | Tests | Coverage |
|--------|-------|----------|
| `tzatziki-db` | 10 scenarios | Insert, insert-only, contains, contains-nothing, null values, ordering, variables, triggers |
| `tzatziki-jpa` | 9 scenarios | Entity CRUD, only-mode, relationships, @Transient, variables, table-level via JPA backend |
| `tzatziki-spring-jpa` | 18 scenarios | Full backward compatibility (unchanged from before refactor) |

## Consequences

### Positive
- Teams using JPA without Spring can depend solely on `tzatziki-jpa`
- Teams doing raw SQL testing can depend solely on `tzatziki-db`
- Clear separation makes each module easier to understand and maintain
- Backend Interface Pattern enables future backends (e.g., Micronaut Data, Quarkus Panache)

### Negative
- Three modules to version and release instead of one
- Slightly more complex dependency graph for Spring users (though transparent via transitivity)

### Risks
- Hook ordering sensitivity: if users register custom `@Before` hooks between order 10-50, they may encounter uninitialized backends
- `PlainJpaBackend` (and `SpringJpaBackend` for entities without repository) use `merge()` through the shared `JpaBackend` defaults in a `RESOURCE_LOCAL` transaction. Entities using `@GeneratedValue(strategy = IDENTITY)` should therefore not be inserted with explicit IDs. Entities with a Spring Data repository keep repository semantics.

## Alternatives Considered

1. **Single module with optional Spring deps** — Rejected: still forces Spring transitive deps on the classpath even if unused.
2. **Two modules (db + spring-jpa)** — Rejected: doesn't address the "JPA without Spring" use case shown in the slides.
3. **Inheritance-based approach** — Rejected: Backend Interface Pattern is more flexible and aligns with the existing `tzatziki-kafka` architecture.

## References

- "Tzatziki JPA module.pdf" slide deck (8 slides) — original proposal
- `tzatziki-kafka` module — Backend Interface Pattern precedent (`KafkaBackend` / `PlainKafkaBackend` / `SpringKafkaBackend`)

## Addendum: Review Fixes (2026-04-29)

Issues found while reviewing the three modules, and how they were addressed:

| Issue | Module | Fix |
|---|---|---|
| **Unvalidated identifiers in SQL strings** | tzatziki-db | Identifiers come from feature files written by test authors, so this is a robustness concern rather than a security boundary. Identifiers from Gherkin steps (tables, columns, cleaned schemas) are validated by `SqlIdentifier` with the regex `^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*$`, giving a clear error instead of a SQL syntax error. Table names read from JDBC metadata (autoclean, triggers) are emitted as double-quoted identifiers via `SqlIdentifier.quoted()`, so mixed-case or special-character tables are handled. |
| **EntityManager lifecycle leak** | tzatziki-jpa | `PlainJpaBackend` stores `Map<Type, EntityManagerFactory>` instead of long-lived `EntityManager` instances, creates fresh EntityManagers per operation, and closes them immediately after use. The expected-field query path also stays inside the backend implementation, so caller code no longer leaks EntityManagers on assertion reads. |
| **NoSuchElementException** | tzatziki-spring-jpa | `SpringJpaBackend.getRepositoryByType()` changed `.findFirst().get()` to `.findFirst().orElseThrow()` with a descriptive error message. |
| **Backend contract ambiguity** | tzatziki-jpa, tzatziki-spring-jpa | Removed `JpaBackend.getEntityManager()` from the shared contract and replaced it with backend-owned `withEntityManager(...)` / `withTransaction(...)` hooks plus shared default CRUD/query/count/truncate logic. Plain JPA and Spring share one API without conflicting caller cleanup rules. |
| **Case-insensitive column matching** | tzatziki-db | `JdbcBackend.resolveColumnTypes()` matches column names with `equalsIgnoreCase()` (PostgreSQL returns lowercase metadata) and resolves `schema.table` names against the right schema. |

Thread-safety hardening of static fields (`volatile`, `synchronized`, `CopyOnWriteArrayList`) was initially added and then
reverted: scenarios share global static state and a single database, so these constructs gave no real guarantee. Steps
and backends are not designed for parallel execution within a JVM.
