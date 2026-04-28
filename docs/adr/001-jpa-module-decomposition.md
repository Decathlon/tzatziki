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

**Purpose**: Database-level test operations using only JDBC.

**Key classes**:
- `DatabaseCleaner` — Truncates tables via raw SQL `Connection`/`Statement`. Supports schema-scoped cleaning and trigger management.
- `InsertionMode` — Enum (`DEFAULT`, `ONLY`) controlling whether to truncate before insert.
- `DatabaseSteps` — Cucumber steps: trigger enable/disable, DataSource registry, `@Before(order=100)` autoclean hook.

**Dependencies**: `tzatziki-core`, `javax.sql` (JDBC API)

**Design decisions**:
- Rewrote `JdbcTemplate`/`DataSourceTransactionManager` usage with plain JDBC (`Connection.setAutoCommit`, `Statement.executeUpdate`)
- DataSource registry is static to allow cross-module registration
- Trigger management is the canonical location (only here, not duplicated in JPA layer)

### Module: `tzatziki-jpa`

**Purpose**: JPA entity testing without Spring, using `EntityManager` directly.

**Key classes**:
- `JpaBackend` — Interface defining the contract: `getEntityManager`, `getDataSource`, `getAllDataSources`, `saveAll`, `findAll`, `count`, `truncate`, `resolveEntityType`
- `PlainJpaBackend` — Default implementation using `EntityManagerFactory` and `Persistence.createEntityManagerFactory()`
- `JpaSteps` — Cucumber steps for entity insert/assert/variable-assignment, `@Before(order=50)` autoclean
- `PersistenceUtil` — Jackson serializer module that skips uninitialized Hibernate proxies and `@Transient` fields
- `EntityGraphUtils` — Builds JPA entity graphs from expected field sets (only fetches asserted columns)

**Dependencies**: `tzatziki-db`, `tzatziki-jackson`, `jakarta.persistence-api 3.2.0`, `hibernate-core 7.0.0.Final`

**Design decisions**:
- **Backend Interface Pattern** (inspired by `tzatziki-kafka`'s `KafkaBackend`): The `JpaBackend` interface allows different implementations (plain JPA vs Spring Data) to be plugged in via `JpaSteps.registerBackend()`.
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
- `PlainJpaBackend` not yet battle-tested in standalone mode (no standalone JPA test suite created yet)

## Alternatives Considered

1. **Single module with optional Spring deps** — Rejected: still forces Spring transitive deps on the classpath even if unused.
2. **Two modules (db + spring-jpa)** — Rejected: doesn't address the "JPA without Spring" use case shown in the slides.
3. **Inheritance-based approach** — Rejected: Backend Interface Pattern is more flexible and aligns with the existing `tzatziki-kafka` architecture.

## References

- "Tzatziki JPA module.pdf" slide deck (8 slides) — original proposal
- `tzatziki-kafka` module — Backend Interface Pattern precedent (`KafkaBackend` / `PlainKafkaBackend` / `SpringKafkaBackend`)
