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

**Purpose**: Database-level test operations using plain JDBC.

**Key classes**:
- `DatabaseCleaner` — Truncates tables via raw SQL `Connection`/`Statement`. Supports schema-scoped cleaning and trigger management.
- `JdbcBackend` — Default `DbBackend` implementation for dynamic INSERT / SELECT / COUNT operations.
- `InsertionMode` — Enum (`DEFAULT`, `ONLY`) controlling whether to truncate before insert.
- `DatabaseSteps` — Cucumber steps: trigger enable/disable, DataSource registry, `@Before(order=100)` autoclean hook.

**Dependencies**: `tzatziki-core`, `javax.sql` (JDBC API)

**Design decisions**:

- Rewrote `JdbcTemplate`/`DataSourceTransactionManager` usage with plain JDBC and `String.formatted(...)`. Table and
  column names come from the feature files and are used as is; table names read from the JDBC metadata (autoclean,
  triggers) are double-quoted.
- DataSource registry is a single static list to allow cross-module registration. Registering a backend replaces it
  with the backend's data sources; `registerDataSource()` adds more sources afterwards.
- JDBC insertions and explicit truncates run in a transaction (`DatabaseCleaner.inTransaction`) regardless of the
  DataSource's autocommit default. Each row is inserted with its own `INSERT`, so omitted columns keep database
  defaults while explicit nulls remain null. Values are bound untyped (`Types.OTHER`) and cast by PostgreSQL to the
  column types.
- Trigger management is the canonical location (only here, not duplicated in JPA layer)
- **PostgreSQL only**: `TRUNCATE ... RESTART IDENTITY CASCADE` and `ALTER TABLE ... DISABLE TRIGGER ALL` are
  PostgreSQL syntax. Supporting other databases would require a dialect abstraction (not part of this split).

### Module: `tzatziki-jpa`

**Purpose**: JPA entity testing without Spring, using `EntityManager` directly.

**Key classes**:
- `JpaBackend` — Concrete backend built from `EntityManagerFactory` → `DataSource` pairs: entity CRUD/query/count/truncate, table-name resolution and table-level `DbBackend` bridging. Exposes two `protected` hooks (`entityManager(Class)`, `qualifiedTableName(Class)`) for subclasses.
- `JpaSteps` — Cucumber steps for entity insert/assert/variable-assignment (cleaning is delegated to `DatabaseSteps`)
- `PersistenceUtil` — Jackson serializer module that skips uninitialized Hibernate proxies and `@Transient` fields
- `EntityGraphUtils` — Builds JPA entity graphs from expected field sets (only fetches asserted columns)

**Dependencies**: `tzatziki-db`, `tzatziki-jackson`, `jakarta.persistence-api` and `hibernate-core` in `provided` scope (versions managed by the Spring Boot BOM; the user's application brings its own JPA implementation)

**Design decisions**:
- **Single class with hooks**: `JpaBackend` is a concrete class registered via `JpaSteps.registerBackend()`; the Spring
  backend extends it and only overrides what differs (repository saves, shared EntityManager, Spring Data `@Table`).
- **Expected-field reads**: `findAll(entityClass, expectedEntities)` applies a fetch graph built from the merged shape of
  all expected entities (`EntityGraphUtils`), so the asserted lazy associations — including those of collection
  elements — are loaded before the EntityManager is closed.
- **Table resolution through `@Table`** (as before the split): table-level steps resolve `table` or `schema.table` from
  the entity's `@Table(name, schema)`, with a fallback on the class name. Table-level steps use entity property names.
- **Table content variable**: `DbBackend.contentOf(table)` returns entities for mapped tables (as before the split) and
  raw rows for JDBC tables.
- `PersistenceUtil.registerTransientAnnotation()` — extensible transient detection; the Spring layer registers `org.springframework.data.annotation.Transient` without polluting the JPA layer with Spring deps.
- `JpaSteps` injects `DatabaseSteps` via Cucumber DI and delegates trigger toggling during inserts to it (single source of truth for trigger state).

### Module: `tzatziki-spring-jpa` (refactored)

**Purpose**: Thin bridge registering Spring Data as the JPA backend.

**Key classes**:
- `SpringJpaBackend` — Extends `JpaBackend`. Inserts always go through the entity's `CrudRepository` (as before the split, so Spring transaction management, JTA and custom repository logic apply). JPA reads and counts use Spring's shared `EntityManager`, which joins the current transaction if any; entities outside the JPA metamodel (e.g. Spring Data JDBC) are read through their repository.
- `SpringJPASteps` — Registers `SpringJpaBackend` in a `@Before(order=10)` hook. Retains Spring-only step definitions: CrudRepository-typed inserts, PagingAndSortingRepository sorted queries.

**Dependencies**: `tzatziki-jpa`, `tzatziki-spring`, Spring Boot starters

**Design decisions**:
- Backward-compatible `autoclean` and `schemasToClean` fields — `SpringJPASteps` and `DatabaseSteps`
  inherit the same fields from `DatabaseConfiguration`, so both APIs configure a single source of truth
  without a hook overwriting either one.
- The pre-split public Java API of `SpringJPASteps` (repository helpers, table/entity step methods) is kept as `@Deprecated(forRemoval = true)` delegates.
- Spring's `@Transient` registered via `PersistenceUtil.registerTransientAnnotation()` in the `@Before` hook.
- `CrudRepository` is used for inserts, for Spring-specific repository steps, and for reading entities that are not part of the JPA metamodel (for example Spring Data JDBC repositories). An entity without repository cannot be inserted (`is not an Entity!`), as before the split.

## Hook Ordering

| Order | Hook | Module | Purpose |
|-------|------|--------|---------|
| 10 | `SpringJPASteps.before()` | spring-jpa | Register SpringJpaBackend |
| 100 | `DatabaseSteps.before()` | db | Autoclean of every registered DataSource (single cleaning hook for all modules) |

## Key Design Principles

1. **Backward compatibility**: All 18 existing spring-jpa test scenarios pass without modification.
2. **Minimal dependency**: Each module only pulls what it needs. `tzatziki-db` has no JPA/Hibernate; `tzatziki-jpa` has no Spring.
3. **Extensibility**: `registerBackend()` accepts any `JpaBackend` subclass (e.g. `SpringJpaBackend`).
4. **Single responsibility**: Trigger management only in `DatabaseSteps`, entity CRUD only in `JpaSteps`, Spring wiring only in `SpringJPASteps`.
5. **Progressive disclosure**: Users pick the layer matching their stack — raw SQL, JPA, or Spring Data.

## DbBackend Interface Pattern

In addition to `JpaBackend` (entity-level operations), a `DbBackend` interface handles **table-level** operations in `tzatziki-db`:

```java
public interface DbBackend {
    void insertRows(String tableName, List<Map<String, Object>> rows);
    List<Map<String, Object>> queryAll(String tableName, List<Map<String, Object>> expectedRows);
    long count(String tableName);
    void truncate(String tableName);
}
```

**Implementations**:
- `JdbcBackend` (default, in `tzatziki-db`) — Pure JDBC. Values are bound untyped (`Types.OTHER`) so that PostgreSQL
  casts them to the column types.
- `JpaBackend` (in `tzatziki-jpa`) — `JpaBackend implements DbBackend`, bridging table-name operations to entity-class
  operations, so every JPA backend (including `SpringJpaBackend`) is automatically a `DbBackend` — no adapter class
  needed. Tables that do not map to a managed entity (join tables, audit tables...) fall back to a `JdbcBackend` on the
  first registered `DataSource`. With several DataSources, such tables must live in the first one.

**Registration flow**:
```
Spring @Before(order=10) → JpaSteps.registerBackend(springJpaBackend)
                         → DatabaseSteps.registerBackend(jpaBackend, dataSources)  // replaces the registered sources
DB  @Before(order=100)   → uses registered DbBackend (or falls back to JdbcBackend)
```

When `tzatziki-jpa` is on the classpath, table-level steps (`the X table will contain`, `the X table contains`) route
through `JpaBackend`, which delegates to entity-class methods like `findAll(entityClass, expectedEntities)`. This
preserves relationship resolution and lazy loading. Without it, they use plain JDBC.

**Key design decision**: `queryAll()` accepts the full `expectedRows` (not just column names) so the JPA adapter can build entity graphs matching the exact structure being asserted — fetching only what's needed, including nested lazy associations matching the assertion depth.

## Test Coverage

| Module | Tests | Coverage |
|--------|-------|----------|
| `tzatziki-db` | 14 scenarios + JUnit | Insert, insert-only, contains, contains-nothing, null values, ordering, variables, triggers |
| `tzatziki-jpa` | 13 scenarios + JUnit | Entity CRUD, only-mode, relationships, @Transient, variables, table-level via JPA backend |
| `tzatziki-spring-jpa` | 28 scenarios + JUnit | The 18 pre-split scenarios unchanged, plus schema-qualified tables, JDBC fallback and the pre-split `SpringJPASteps` constructor |

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
- Requires Hibernate ORM 6 (fetch graph hint) and PostgreSQL (cleanup/trigger SQL, untyped parameters).
- Hook ordering sensitivity: if users register custom `@Before` hooks between order 10-50, they may encounter uninitialized backends
- `JpaBackend` (without Spring) uses `merge()` in a `RESOURCE_LOCAL` transaction. Entities using `@GeneratedValue(strategy = IDENTITY)` should therefore not be inserted with explicit IDs. `SpringJpaBackend` keeps repository semantics.

## Alternatives Considered

1. **Single module with optional Spring deps** — Rejected: still forces Spring transitive deps on the classpath even if unused.
2. **Two modules (db + spring-jpa)** — Rejected: doesn't address the "JPA without Spring" use case.
3. **Backend interface with default methods and a separate plain implementation** (as `tzatziki-kafka`) — Initially
   implemented, then simplified into a concrete `JpaBackend` extended by `SpringJpaBackend`: both backends share almost
   all their logic, so the interface only added indirection.

## References

- `tzatziki-kafka` module — Backend Interface Pattern precedent (`KafkaBackend` / `PlainKafkaBackend` / `SpringKafkaBackend`)

## Addendum: Review Fixes (2026-04-29)

Issues found while reviewing the three modules, and how they were addressed:

| Issue | Module | Fix |
|---|---|---|
| **Identifiers in SQL strings** | tzatziki-db | Identifiers come from feature files written by test authors, so this is not a security boundary and they are used as is (as before the split). Table names read from JDBC metadata (autoclean, triggers) are double-quoted, so mixed-case or special-character tables are handled. |
| **EntityManager lifecycle leak** | tzatziki-jpa | `JpaBackend` stores `Map<Class, EntityManagerFactory>` instead of long-lived `EntityManager` instances, creates fresh EntityManagers per operation, and closes them immediately after use (the Spring shared EntityManager ignores `close()`). |
| **NoSuchElementException** | tzatziki-spring-jpa | `SpringJpaBackend.getRepositoryByType()` changed `.findFirst().get()` to `.findFirst().orElseThrow()` with a descriptive error message. |
| **Backend contract ambiguity** | tzatziki-jpa, tzatziki-spring-jpa | Removed `JpaBackend.getEntityManager()` from the public contract: EntityManagers are obtained through the `protected entityManager(Class)` hook, used internally only. |
| **Shared EntityManager and transactions** | tzatziki-spring-jpa | Spring saves never use `getTransaction()` (forbidden on shared EntityManagers): they always go through the repository. Reads use a shared EntityManager created from each `LocalContainerEntityManagerFactoryBean`. `SpringJPASteps` no longer takes a `List<EntityManager>` constructor argument. |

Thread-safety hardening of static fields (`volatile`, `synchronized`, `CopyOnWriteArrayList`) was initially added and then
reverted: scenarios share global static state and a single database, so these constructs gave no real guarantee. Steps
and backends are not designed for parallel execution within a JVM.
