# ADR-001: Decomposition of tzatziki-spring-jpa into Three Layered Modules

## Status

Accepted

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
- `SqlIdentifier`, `InsertSpec`, `SelectSpec`, `CountSpec`, `TruncateSpec`, `SqlRenderer` — Internal SQL DSL that
  centralizes identifier validation and SQL rendering while keeping the module JDBC-only.
- `InsertionMode` — Enum (`DEFAULT`, `ONLY`) controlling whether to truncate before insert.
- `DatabaseSteps` — Cucumber steps: trigger enable/disable, DataSource registry, `@Before(order=100)` autoclean hook.

**Dependencies**: `tzatziki-core`, `javax.sql` (JDBC API)

**Design decisions**:

- Rewrote `JdbcTemplate`/`DataSourceTransactionManager` usage with plain JDBC, and introduced an internal SQL DSL (
  `SqlIdentifier`, statement specs, `SqlRenderer`) so `JdbcBackend` no longer assembles SQL ad hoc in each method.
- DataSource registry is static to allow cross-module registration
- Trigger management is the canonical location (only here, not duplicated in JPA layer)

### Module: `tzatziki-jpa`

**Purpose**: JPA entity testing without Spring, using `EntityManager` directly.

**Key classes**:
- `JpaBackend` — Interface defining the contract: `getDataSource`, `getAllDataSources`, `saveAll`, `findAll`, `findAllWithExpectedFields`, `count`, `truncate`, `resolveEntityType`
- `PlainJpaBackend` — Default implementation using `EntityManagerFactory` and `Persistence.createEntityManagerFactory()`
- `JpaSteps` — Cucumber steps for entity insert/assert/variable-assignment, `@Before(order=50)` autoclean
- `PersistenceUtil` — Jackson serializer module that skips uninitialized Hibernate proxies and `@Transient` fields
- `EntityGraphUtils` — Builds JPA entity graphs from expected field sets (only fetches asserted columns)

**Dependencies**: `tzatziki-db`, `tzatziki-jackson`, `jakarta.persistence-api 3.2.0`, `hibernate-core 7.0.0.Final`

**Design decisions**:
- **Backend Interface Pattern** (inspired by `tzatziki-kafka`'s `KafkaBackend`): The `JpaBackend` interface allows different implementations (plain JPA vs Spring Data) to be plugged in via `JpaSteps.registerBackend()`.
- **Backend-owned query lifecycle**: expected-field reads stay inside `JpaBackend.findAllWithExpectedFields(...)`. Callers never receive a raw `EntityManager`, so plain JPA and Spring can each manage persistence-context lifecycle safely.
- `PersistenceUtil.registerTransientAnnotation()` — extensible transient detection; the Spring layer registers `org.springframework.data.annotation.Transient` without polluting the JPA layer with Spring deps.
- `JpaSteps` injects `DatabaseSteps` via Cucumber DI and delegates trigger toggling during inserts to it (single source of truth for trigger state).
- Table name resolution uses plain `class.getAnnotation(jakarta.persistence.Table.class)` — no `AnnotationUtils`.

### Module: `tzatziki-spring-jpa` (refactored)

**Purpose**: Thin bridge registering Spring Data as the JPA backend.

**Key classes**:
- `SpringJpaBackend` — Implements `JpaBackend` using Spring's `CrudRepository`, `LocalContainerEntityManagerFactoryBean`, and `ApplicationContext`
- `SpringJPASteps` — Slimmed from ~380 to ~145 lines. `@Before(order=10)` hook registers `SpringJpaBackend` and propagates configuration. Retains Spring-only step definitions: CrudRepository-typed inserts, PagingAndSortingRepository sorted queries.

**Dependencies**: `tzatziki-jpa`, `tzatziki-spring`, Spring Boot starters

**Design decisions**:
- Backward-compatible `schemasToClean` field — existing test code sets `SpringJPASteps.schemasToClean`; the `@Before` hook syncs it to `JpaSteps.schemasToClean`.
- Spring's `@Transient` registered via `PersistenceUtil.registerTransientAnnotation()` in the `@Before` hook.
- Spring Data Relational `@Table` annotation support remains Spring-only.

## Hook Ordering

| Order | Hook | Module | Purpose |
|-------|------|--------|---------|
| 10 | `SpringJPASteps.before()` | spring-jpa | Register SpringJpaBackend, propagate config |
| 50 | `JpaSteps.before()` | jpa | Autoclean via DatabaseCleaner |
| 100 | `DatabaseSteps.before()` | db | DB-level autoclean (standalone usage) |

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
- `JpaDbBackend` (adapter, in `tzatziki-jpa`) — Wraps `JpaBackend` to implement `DbBackend`. Uses JPA entity graph optimization and Hibernate `initialize()` for lazy association traversal.

**Registration flow**:
```
Spring @Before(order=10) → JpaSteps.registerBackend(springJpaBackend)
JPA @Before(order=50)    → DatabaseSteps.registerBackend(new JpaDbBackend(jpaBackend))
DB  @Before(order=100)   → uses registered DbBackend (or falls back to JdbcBackend)
```

When `tzatziki-jpa` is on the classpath, table-level steps (`the X table will contain`, `the X table contains`) route through `JpaBackend.findAllWithExpectedFields(...)`, benefiting from relationship resolution and lazy loading initialization while keeping persistence-context ownership inside the backend implementation. Without it, they use plain JDBC.

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
- Dual autoclean at order=50 and order=100 when all three modules are on the classpath (idempotent but slightly wasteful)

### Risks
- Hook ordering sensitivity: if users register custom `@Before` hooks between order 10-50, they may encounter uninitialized backends
- `PlainJpaBackend` uses `merge()` which requires entities to NOT use `@GeneratedValue(strategy = IDENTITY)` when providing explicit IDs. Users with IDENTITY generation should let IDs be auto-assigned or use the Spring layer.

## Alternatives Considered

1. **Single module with optional Spring deps** — Rejected: still forces Spring transitive deps on the classpath even if unused.
2. **Two modules (db + spring-jpa)** — Rejected: doesn't address the "JPA without Spring" use case shown in the slides.
3. **Inheritance-based approach** — Rejected: Backend Interface Pattern is more flexible and aligns with the existing `tzatziki-kafka` architecture.

## References

- "Tzatziki JPA module.pdf" slide deck (8 slides) — original proposal
- `tzatziki-kafka` module — Backend Interface Pattern precedent (`KafkaBackend` / `PlainKafkaBackend` / `SpringKafkaBackend`)

## Addendum: Security & Reliability Hardening (2026-04-29)

A deep code review of all three modules identified 8 issues (4 Critical, 4 High). All were fixed:

### Critical Fixes

| Issue | Module | Fix |
|---|---|---|
| **SQL Injection via string interpolation** | tzatziki-db | Added `DatabaseCleaner.validateIdentifier()` with strict regex `^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*$`. Called before every `String.formatted()` in `DatabaseCleaner`, `JdbcBackend`. Column names also validated. |
| **EntityManager lifecycle leak** | tzatziki-jpa | `PlainJpaBackend` stores `Map<Type, EntityManagerFactory>` instead of long-lived `EntityManager` instances, creates fresh EntityManagers per operation, and closes them immediately after use. The expected-field query path now also stays inside the backend implementation, so caller code no longer leaks EntityManagers on assertion reads. |
| **Thread-unsafe static fields** | tzatziki-db, tzatziki-jpa | `DatabaseSteps.backend` and `JpaSteps.backend` marked `volatile`. `registeredDataSources` changed to `CopyOnWriteArrayList`. `resetTablesNotToBeCleanedFilter()` uses `synchronized` block for atomic clear+addAll. |
| **NoSuchElementException** | tzatziki-spring-jpa | `SpringJpaBackend.getRepositoryByType()` changed `.findFirst().get()` to `.findFirst().orElseThrow()` with descriptive error message. |

### High Fixes

| Issue | Module | Fix |
|---|---|---|
| **Double-checked locking without volatile** | tzatziki-spring-jpa | `crudRepositoryByClass`, `entityManagerByClass`, `entityClassByTableName` fields marked `volatile` to ensure visibility across threads in lazy-init pattern. |
| **Backend contract ambiguity** | tzatziki-jpa, tzatziki-spring-jpa | Removed `JpaBackend.getEntityManager()` from the shared contract and replaced it with backend-owned `findAllWithExpectedFields()`. Plain JPA and Spring now share one safe API without conflicting caller cleanup rules. |
| **registerDataSource race condition** | tzatziki-db | `registerDataSource()` method marked `synchronized` to make the contains+add check-then-act atomic. |
| **Case-insensitive column matching** | tzatziki-db | `JdbcBackend.resolveColumnTypes()` now uses `equalsIgnoreCase()` for column name matching (PostgreSQL returns lowercase metadata). |
