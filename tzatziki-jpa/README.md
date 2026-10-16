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
