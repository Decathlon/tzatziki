# User Provided Header
Tzatziki DB module reference (plain JDBC, no JPA nor Spring).
- DatabaseSteps.java defines @Given/@When/@Then patterns for table-level SQL fixtures and assertions (`the <table> table will contain / contains / contains nothing`, `<var> is the <table> table content`), trigger toggling and automatic cleanup (`autoclean`, `schemasToClean`). Table names may be schema-qualified (`schema.table`) and rows use column names.
- These table steps are also available, unchanged, with tzatziki-jpa and tzatziki-spring-jpa (which route them to the mapped entity).
- .feature files demonstrate valid table step usage with YAML doc strings and tables for database state.
- Prefer YAML (`"""yml`) for fixtures and expected data.


# Directory Structure
```
tzatziki-db/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                DatabaseSteps.java
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                db.feature
```

# Files

## File: tzatziki-db/src/main/java/com/decathlon/tzatziki/steps/DatabaseSteps.java
```java
package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import javax.sql.DataSource;
import java.util.*;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.InsertionMode.INSERTION_MODE;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure database Cucumber step definitions for table-level operations.
 * <p>
 * Uses {@link DbBackend} to perform INSERT, SELECT, COUNT, and TRUNCATE.
 * By default, a {@link JdbcBackend} is used with the first registered DataSource.
 * Upper layers (tzatziki-jpa, tzatziki-spring-jpa) can override the backend via
 * {@link #registerBackend(DbBackend, List)} to use JPA entity resolution and entity graphs.
 */
@SuppressWarnings("java:S100")
public class DatabaseSteps extends DatabaseConfiguration {

    public static final String TABLE_PATTERN = "([^ ]+)";

    private static final List<DataSource> registeredDataSources = new ArrayList<>();
    private static DbBackend backend;
    private boolean disableTriggers = true;

    private final ObjectSteps objects;

    static {
        DynamicTransformers.register(InsertionMode.class, InsertionMode::parse);
    }

    public DatabaseSteps(ObjectSteps objects) {
        this.objects = objects;
    }

    /**
     * Replace the backend (e.g. by JpaSteps) and the registered data sources.
     * Additional data sources can then be added with {@link #registerDataSource}.
     */
    public static void registerBackend(DbBackend dbBackend, List<DataSource> dataSources) {
        registeredDataSources.clear();
        dataSources.forEach(DatabaseSteps::registerDataSource);
        backend = dbBackend;
    }

    /**
     * Get the currently registered backend. Returns null if none registered.
     */
    public static DbBackend getBackend() {
        return backend;
    }

    /**
     * Register a DataSource to be managed by DatabaseSteps (cleaned on @Before, triggers managed).
     */
    public static void registerDataSource(DataSource dataSource) {
        if (!registeredDataSources.contains(dataSource)) {
            registeredDataSources.add(dataSource);
        }
    }

    /**
     * Clear all registered data sources (for test isolation).
     */
    public static void clearDataSources() {
        registeredDataSources.clear();
        backend = null;
    }

    public static List<DataSource> getRegisteredDataSources() {
        return List.copyOf(registeredDataSources);
    }

    @Before(order = 100)
    public void before() {
        if (autoclean) {
            getRegisteredDataSources().forEach(dataSource -> {
                DatabaseCleaner.clean(dataSource, schemasToClean);
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.enable);
            });
        }
    }

    private static DbBackend requireBackend() {
        if (backend != null) return backend;
        List<DataSource> dataSources = getRegisteredDataSources();
        if (dataSources.isEmpty()) {
            throw new IllegalStateException("No DbBackend or DataSource registered. Call DatabaseSteps.registerDataSource(...) or DatabaseSteps.registerBackend(...) first.");
        }
        return new JdbcBackend(dataSources.get(0));
    }

    // ---- Table-level step definitions ----

    @Given(THAT + GUARD + "the " + TABLE_PATTERN + " table will contain" + INSERTION_MODE + ":$")
    public void the_table_will_contain(Guard guard, String table, InsertionMode insertionMode, Object content) {
        guard.in(objects, () -> {
            DbBackend activeBackend = requireBackend();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List) Mapper.readAsAListOf(objects.resolve(content), Map.class);
            withTriggersDisabledIfNeeded(() -> {
                if (insertionMode == InsertionMode.ONLY) {
                    activeBackend.truncate(table);
                }
                activeBackend.insertRows(table, rows);
            });
        });
    }

    @Then(THAT + GUARD + "the " + TABLE_PATTERN + " table (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_table_contains(Guard guard, String table, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> expectedRows = (List) Mapper.readAsAListOf(objects.resolve(content), Map.class);
            List<Map<String, Object>> actualRows = requireBackend().queryAll(table, expectedRows);
            comparison.compare(actualRows, expectedRows);
        });
    }

    @Then(THAT + GUARD + "the " + TABLE_PATTERN + " table (?:still )?contains nothing$")
    public void the_table_contains_nothing(Guard guard, String table) {
        guard.in(objects, () -> assertThat(requireBackend().count(table)).isZero());
    }

    @Then(THAT + GUARD + VARIABLE + " is the " + TABLE_PATTERN + " table content$")
    public void add_table_content_to_variable(Guard guard, String name, String table) {
        guard.in(objects, () -> objects.add(name, requireBackend().contentOf(table)));
    }

    // ---- Trigger management ----

    @Given(THAT + GUARD + "the triggers are (enable|disable)d$")
    public void enable_triggers(Guard guard, String action) {
        guard.in(objects, () -> disableTriggers = action.equals("disable"));
    }

    public boolean isDisableTriggers() {
        return disableTriggers;
    }

    /**
     * Runs the given insertion with the triggers disabled (unless "the triggers are enabled" was set),
     * re-enabling them afterwards even if the insertion fails.
     */
    public void withTriggersDisabledIfNeeded(Runnable insertion) {
        boolean toggleTriggers = disableTriggers;
        if (toggleTriggers) {
            setTriggers(DatabaseCleaner.TriggerStatus.disable);
        }
        try {
            insertion.run();
        } finally {
            if (toggleTriggers) {
                setTriggers(DatabaseCleaner.TriggerStatus.enable);
            }
        }
    }

    private static void setTriggers(DatabaseCleaner.TriggerStatus status) {
        getRegisteredDataSources().forEach(dataSource -> DatabaseCleaner.setTriggers(dataSource, schemasToClean, status));
    }
}
```

## File: tzatziki-db/src/test/resources/com/decathlon/tzatziki/steps/db.feature
```
@db
Feature: Database operations

  Scenario: Database cleaner truncates tables
    Given the test table has data
    Then after cleaning the test table is empty

  # ---- Table-level step definitions (DbBackend) ----

  Scenario: Insert data into a table using "will contain"
    Given that the products table will contain:
      | id | name     | price |
      | 1  | Widget   | 9.99  |
      | 2  | Gadget   | 19.99 |
    Then the products table contains:
      | id | name     | price |
      | 1  | Widget   | 9.99  |
      | 2  | Gadget   | 19.99 |

  Scenario: Insert data with "only" mode truncates existing rows first
    Given that the products table will contain:
      | id | name     | price |
      | 1  | Widget   | 9.99  |
    Given that the products table will contain only:
      | id | name     | price |
      | 3  | Doohickey | 29.99 |
    Then the products table contains:
      | id | name      | price |
      | 3  | Doohickey | 29.99 |

  Scenario: Assert table contains nothing after truncation
    Given that the products table will contain:
      | id | name   | price |
      | 1  | Widget | 9.99  |
    Given that the products table will contain only:
      | id | name | price |
    Then the products table contains nothing

  Scenario: Table content can be stored in a variable
    Given that the products table will contain:
      | id | name   | price |
      | 1  | Widget | 9.99  |
      | 2  | Gadget | 19.99 |
    Then product_list is the products table content
    And product_list contains only:
      | id | name   | price |
      | 1  | Widget | 9.99  |
      | 2  | Gadget | 19.99 |

  Scenario: Assert table contains exactly checks all columns
    Given that the products table will contain:
      | id | name   | price |
      | 1  | Widget | 9.99  |
    Then the products table contains exactly:
      | id | name   | price |
      | 1  | Widget | 9.99  |
    And a java.lang.AssertionError is thrown when the products table contains exactly:
      | id | name   |
      | 1  | Widget |

  Scenario: Insert data with null values
    Given that the products table will contain:
      | id | name   | price |
      | 1  | Widget |       |
    Then the products table contains:
      | id | name   |
      | 1  | Widget |

  Scenario: Insert and verify multiple rows with ordering
    Given that the products table will contain:
      | id | name     | price |
      | 3  | Charlie  | 30.00 |
      | 1  | Alpha    | 10.00 |
      | 2  | Bravo    | 20.00 |
    Then the products table contains at least:
      | id | name    | price |
      | 1  | Alpha   | 10.00 |
      | 2  | Bravo   | 20.00 |

  Scenario: Triggers are disabled during insertion
    Given that the triggered_table table will contain:
      | id | name  |
      | 1  | test1 |
    Then the audit_log table contains nothing

  Scenario: Triggers can be re-enabled for insertion
    Given that the triggers are enabled
    Given that the triggered_table table will contain:
      | id | name  |
      | 2  | test2 |
    Then the audit_log table contains:
      | table_name      | operation |
      | triggered_table | INSERT    |

  Scenario: Tables whose names require quoting are cleaned
    Given the "audit-events" table has a row
    And the "MixedCase" table has a row
    Then after cleaning the "audit-events" table is empty
    And after cleaning the "MixedCase" table is empty

  Scenario: Values are converted using the column types of a schema-qualified table
    Given that the inventory.stocks table will contain:
      | id | quantity | available | updated_on | updated_at           |
      | 1  | 5        | true      | 2026-04-28 | 2026-04-28T10:15:30Z |
    Then the inventory.stocks table contains only:
      | id | quantity | available | updated_on | updated_at           |
      | 1  | 5        | true      | 2026-04-28 | 2026-04-28T10:15:30Z |

  Scenario: Tables excluded from cleaning are kept
    Given the test table has data
    Then tables excluded from cleaning keep their data until the filter is reset

  Scenario: Triggers of the default schema can be toggled
    Then triggers of the default schema can be toggled
```
