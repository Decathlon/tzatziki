# User Provided Header
Tzatziki JPA module reference (plain JPA / Hibernate, no Spring).
- JpaSteps.java defines @Given/@When/@Then patterns for JPA entity fixtures and assertions (`the <Entity> entities will contain / contain / contain nothing`, `<var> is the <Entity> entities`). It builds on the table steps of the tzatziki-db module (see steps-db.md): with a JpaBackend, table steps are routed to the entity whose @Table matches (rows use entity property names), and tables without entity (e.g. join tables) go through JDBC.
- .feature files demonstrate valid entity and table step usage with YAML doc strings and tables.
- Prefer YAML (`"""yml`) for entity fixtures and expected data.


# Directory Structure
```
tzatziki-jpa/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                JpaSteps.java
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                jpa.feature
```

# Files

## File: tzatziki-jpa/src/main/java/com/decathlon/tzatziki/steps/JpaSteps.java
```java
package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.InsertionMode.INSERTION_MODE;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure JPA Cucumber step definitions for entity-typed CRUD operations.
 * <p>
 * Table-level steps (by table name) are in {@link DatabaseSteps}, which uses the registered
 * {@link JpaBackend} as its {@link DbBackend}.
 * <p>
 * No Spring dependency. Requires Hibernate ORM 6 as JPA provider.
 */
@SuppressWarnings("java:S100")
public class JpaSteps {

    private static JpaBackend backend;

    static {
        DynamicTransformers.register(Type.class, TypeParser::parse);
        if (Mapper.isJackson3()) {
            Jackson3Mapper.with(objectMapper -> objectMapper.rebuild().addModule(Jackson3PersistenceUtil.getMapperModule()).build());
        } else {
            JacksonMapper.with(objectMapper -> objectMapper.registerModule(PersistenceUtil.getMapperModule()));
        }
    }

    private final ObjectSteps objects;
    private final DatabaseSteps databaseSteps;

    public JpaSteps(ObjectSteps objects, DatabaseSteps databaseSteps) {
        this.objects = objects;
        this.databaseSteps = databaseSteps;
    }

    /**
     * Register the JpaBackend, also used by DatabaseSteps for the table-level steps.
     * Called by upper layers (e.g., SpringJPASteps @Before hook) or directly in standalone JPA tests.
     */
    public static void registerBackend(JpaBackend jpaBackend) {
        backend = jpaBackend;
        DatabaseSteps.registerBackend(jpaBackend, jpaBackend.getAllDataSources());
    }

    public static JpaBackend getBackend() {
        return backend;
    }

    private static JpaBackend requireBackend() {
        if (backend == null) {
            throw new IllegalStateException("No JpaBackend registered. Call JpaSteps.registerBackend(...) first.");
        }
        return backend;
    }

    // ---- Entity-typed step definitions (require JPA class resolution) ----

    @Given(THAT + GUARD + "the " + TYPE + " entities will contain" + INSERTION_MODE + ":$")
    public void the_entities_will_contain(Guard guard, Type type, InsertionMode insertionMode, Object content) {
        guard.in(objects, () -> insertEntitiesByType(type, insertionMode, objects.resolve(content)));
    }

    @Then(THAT + GUARD + "the " + TYPE + " entities (?:still )?contain" + COMPARING_WITH + ":$")
    public void the_entities_contain(Guard guard, Type type, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            Class<?> entityClass = (Class<?>) type;
            List<Map> expectedEntities = Mapper.readAsAListOf(objects.resolve(content), Map.class);
            List<?> actualEntities = requireBackend().findAll(entityClass, expectedEntities);
            comparison.compare(actualEntities, expectedEntities);
        });
    }

    @Then(THAT + GUARD + "the " + TYPE + " entities (?:still )?contain nothing$")
    public void the_entities_contain_nothing(Guard guard, Type type) {
        guard.in(objects, () -> assertThat(requireBackend().count((Class<?>) type)).isZero());
    }

    @Then(THAT + GUARD + VARIABLE + " is the " + TYPE + " entities$")
    public void add_entities_to_variable(Guard guard, String name, Type type) {
        guard.in(objects, () -> objects.add(name, requireBackend().findAll((Class<?>) type)));
    }

    // ---- internal methods ----

    @SuppressWarnings("unchecked")
    private <E> void insertEntitiesByType(Type type, InsertionMode insertionMode, String entities) {
        if (!(type instanceof Class<?>)) return;
        Class<E> entityClass = (Class<E>) type;
        doInsert(entityClass, insertionMode, entities);
    }

    private <E> void doInsert(Class<E> entityClass, InsertionMode insertionMode, String entities) {
        JpaBackend activeBackend = requireBackend();
        List<E> parsedEntities = Mapper.readAsAListOf(entities, entityClass);
        databaseSteps.withTriggersDisabledIfNeeded(() -> {
            if (insertionMode == InsertionMode.ONLY) {
                activeBackend.truncate(entityClass);
            }
            activeBackend.saveAll(entityClass, parsedEntities);
        });
    }
}
```

## File: tzatziki-jpa/src/test/resources/com/decathlon/tzatziki/steps/jpa.feature
```
Feature: JPA entity operations

  Scenario: Insert entities using "will contain"
    Given that the Item entities will contain:
      | id | name   | price |
      | 1  | Laptop | 999.99 |
      | 2  | Mouse  | 29.99  |
    Then the Item entities contain:
      | id | name   | price  |
      | 1  | Laptop | 999.99 |
      | 2  | Mouse  | 29.99  |

  Scenario: Insert entities with "only" mode truncates first
    Given that the Item entities will contain:
      | id | name    | price |
      | 1  | OldItem | 10.0  |
    And that the Item entities will contain only:
      | id | name    | price |
      | 2  | NewItem | 20.0  |
    Then the Item entities contain:
      | id | name    | price |
      | 2  | NewItem | 20.0  |

  Scenario: Assert entities contain nothing
    Then the Item entities contain nothing

  Scenario: Insert entities with null values
    Given that the Item entities will contain:
      | id | name   | price | description |
      | 1  | NoDesc | 5.00  |             |
    Then the Item entities contain:
      | id | name   | description |
      | 1  | NoDesc |             |

  Scenario: Store entities in a variable
    Given that the Item entities will contain:
      | id | name   | price |
      | 1  | Widget | 15.00 |
    Then items is the Item entities
    And items contains:
      | id | name   |
      | 1  | Widget |

  Scenario: Insert entities with relationship
    Given that the Category entities will contain:
      | id | name        |
      | 1  | Electronics |
    And that the Item entities will contain:
      | id | name   | price  | category.id |
      | 1  | Tablet | 499.99 | 1           |
    Then the Item entities contain:
      | id | name   | category.id | category.name |
      | 1  | Tablet | 1           | Electronics   |

  Scenario: Insert entities with @Transient fields ignored
    Given that the Item entities will contain:
      | id | name   | price | internalNote |
      | 1  | Secret | 0.01  | hidden       |
    Then the Item entities contain:
      | id | name   | price |
      | 1  | Secret | 0.01  |

  Scenario: Table-level steps work via JPA backend
    Given that the items table will contain:
      | id | name  | price |
      | 1  | Chair | 75.0  |
    Then the items table contains:
      | id | name  | price |
      | 1  | Chair | 75.0  |

  Scenario: Table-level "contains nothing" works via JPA backend
    Then the items table contains nothing

  Scenario: Lazy associations are loaded for every expected row, not only the first one
    Given that the Category entities will contain:
      | id | name        |
      | 1  | Electronics |
    And that the Item entities will contain:
      | id | name | price |
      | 1  | Pen  | 1.00  |
    And that the Item entities will contain:
      | id | name   | price  | category.id |
      | 2  | Tablet | 499.99 | 1           |
    Then the Item entities contain:
      """yml
      - id: 1
        name: Pen
      - id: 2
        name: Tablet
        category:
          name: Electronics
      """


  Scenario: Lazy associations of collection elements are loaded
    Given that the Category entities will contain:
      | id | name        |
      | 1  | Electronics |
    And that the Item entities will contain:
      | id | name   | price  | category.id |
      | 1  | Tablet | 499.99 | 1           |
    And that the Basket entities will contain:
      | id |
      | 1  |
    And that the basket_items table will contain:
      | basket_id | item_id |
      | 1         | 1       |
    Then the Basket entities contain:
      """yml
      - id: 1
        items:
          - name: Tablet
            category:
              name: Electronics
      """

  Scenario: Table-level steps replace the rows with "only" and store the entities of the table
    Given that the items table will contain:
      | id | name  | price |
      | 1  | Chair | 75.0  |
    And that the items table will contain only:
      | id | name  | price |
      | 2  | Table | 150.0 |
    Then the items table contains only:
      | id | name  | price |
      | 2  | Table | 150.0 |
    And itemsContent is the items table content
    And itemsContent[0].name is equal to "Table"

  Scenario: Tables without entity are handled through JDBC
    Given that the Item entities will contain:
      | id | name   | price  |
      | 1  | Tablet | 499.99 |
    And that the Basket entities will contain:
      | id |
      | 1  |
    And that the basket_items table will contain:
      | basket_id | item_id |
      | 1         | 1       |
    Then the basket_items table contains only:
      | basket_id | item_id |
      | 1         | 1       |
    And the BASKET_ITEMS table contains only:
      | basket_id | item_id |
      | 1         | 1       |
    And links is the basket_items table content
    And links[0].item_id is equal to 1
    But when the basket_items table will contain only:
      | basket_id | item_id |
    Then the basket_items table contains nothing
```
