# tzatziki-jpa

Cucumber step definitions for JPA entity and table testing **without Spring**.

Includes all table-level steps from [`tzatziki-db`](../tzatziki-db/README.md) (bridged through JPA entity resolution and falling back to JDBC for non-entity tables).

## Dependency

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-jpa</artifactId>
    <version>${tzatziki.version}</version>
    <scope>test</scope>
</dependency>
```

`jakarta.persistence-api` and `hibernate-core` are declared as `provided` so your application supplies its own JPA runtime.

## Requirements

- **Hibernate ORM 6.2+** as JPA provider: table and column names are read from the Hibernate mapping metamodel
  (naming strategy, `@Table(schema = ...)`, `@Column(name = ...)` are honoured). Entity CRUD steps rely on the JPA API
  only, but table resolution and lazy-loading handling are Hibernate-specific.
- **PostgreSQL**, as for [`tzatziki-db`](../tzatziki-db/README.md#supported-databases).

## Configuration

Register a `PlainJpaBackend` in a Cucumber `@Before(order = 1)` hook:

```java
public class JpaTestSteps {

    private static final DataSource dataSource = ...;
    private static final EntityManagerFactory entityManagerFactory = ...;

    @Before(order = 1)
    public void registerBackend() {
        JpaSteps.registerBackend(new PlainJpaBackend(entityManagerFactory, dataSource));
    }
}
```

Registering a new backend replaces the previous backend's DataSources for cleanup and trigger
management. DataSources explicitly registered with `DatabaseSteps.registerDataSource(...)` are
retained; call `DatabaseSteps.clearDataSources()` when those test-owned sources are no longer needed.

## Available Steps

```gherkin
Given that the Item entities will contain:
  | id | name   | price  |
  | 1  | Laptop | 999.99 |

Given that the Item entities will contain only:
  | id | name   | price  |
  | 1  | Laptop | 999.99 |

Then the Item entities contain:
  | id | name   | price  |
  | 1  | Laptop | 999.99 |

Then the Item entities contain nothing

Then items is the Item entities
```

## Table-level steps

Table-level steps from `tzatziki-db` are routed to the entity mapped on the table:

```gherkin
# column names (first_name) and property names (lastName) are both accepted
Given that the users table will contain:
  | id | first_name | lastName |
  | 1  | Darth      | Vader    |

# schema-qualified table names are resolved through the entity mapping
Then the store.products table contains:
  | id | name   |
  | 1  | Widget |

# the variable contains the entities (not raw rows) when the table is mapped
Then users is the users table content
```

Tables that are not mapped to an entity (join tables, audit tables, ...) are handled with plain JDBC on the
first registered `DataSource`. A warning is logged once per table when this fallback is used.
