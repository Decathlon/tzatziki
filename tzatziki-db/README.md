# tzatziki-db

Cucumber step definitions for table-level SQL/JDBC database testing **without JPA or Spring**.

## Supported databases

Only **PostgreSQL** is supported: cleanup uses `TRUNCATE ... RESTART IDENTITY CASCADE` and trigger management uses
`ALTER TABLE ... DISABLE|ENABLE TRIGGER ALL`. Other databases will fail on cleanup and trigger steps.

## Dependency

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-db</artifactId>
    <version>${tzatziki.version}</version>
    <scope>test</scope>
</dependency>
```

## Configuration

Register your `DataSource` with `DatabaseSteps.registerDataSource(...)` in your test setup:

```java
public class DatabaseTestSteps {

    static {
        DataSource dataSource = ...;
        DatabaseSteps.registerDataSource(dataSource);
        DatabaseSteps.schemasToClean = List.of("public");
    }
}
```

The JDBC backend commits each insertion and explicit truncate even when the DataSource uses
`autoCommit=false`, and rolls back the entire insertion if any batch fails. Rows retain their input
order. Omitted columns use database defaults; explicitly supplied `null` values remain SQL `NULL`.

## Available Steps

```gherkin
Given that the products table will contain:
  | id | name   | price |
  | 1  | Widget | 9.99  |

Given that the products table will contain only:
  | id | name   | price |
  | 1  | Widget | 9.99  |

Then the products table contains:
  | id | name   | price |
  | 1  | Widget | 9.99  |

Then the products table contains nothing

Then product_list is the products table content

Then the store.products table contains:
  | id | name   |
  | 1  | Widget |

Given that the triggers are enabled
Given that the triggers are disabled
```

Table names may be schema-qualified (`schema.table`). Table and column identifiers must match
`[a-zA-Z_][a-zA-Z0-9_]*`.
