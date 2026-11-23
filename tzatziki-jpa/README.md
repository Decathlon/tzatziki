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

- **Hibernate ORM 6** as JPA provider (fetch graphs and lazy-loading handling are Hibernate-specific).
- **PostgreSQL**, as for [`tzatziki-db`](../tzatziki-db/README.md#supported-databases).

## Configuration

Register a `JpaBackend` in a Cucumber `@Before(order = 1)` hook:

```java
public class JpaTestSteps {

    private static final DataSource dataSource = ...;
    private static final EntityManagerFactory entityManagerFactory = ...;

    @Before(order = 1)
    public void registerBackend() {
        JpaSteps.registerBackend(new JpaBackend(entityManagerFactory, dataSource));
    }
}
```

Registering a backend replaces the DataSources used for cleanup and trigger management by the backend's ones.
Additional DataSources can then be added with `DatabaseSteps.registerDataSource(...)`.

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

Table-level steps from `tzatziki-db` are routed to the entity whose `@Table` name matches the table
(rows use the entity property names):

```gherkin
Given that the users table will contain:
  | id | firstName | lastName |
  | 1  | Darth     | Vader    |

# schema-qualified table names are resolved through @Table(schema = ...)
Then the store.products table contains:
  | id | name   |
  | 1  | Widget |

# the variable contains the entities (not raw rows) when the table is mapped
Then users is the users table content
```

Tables that are not mapped to an entity (join tables, audit tables, ...) are handled with plain JDBC on the
first registered `DataSource`.
