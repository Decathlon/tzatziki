# tzatziki-db

Cucumber step definitions for table-level SQL/JDBC database testing **without JPA or Spring**.

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

Given that the triggers are enabled
Given that the triggers are disabled
```
