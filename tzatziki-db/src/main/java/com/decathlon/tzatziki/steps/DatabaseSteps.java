package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

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
 * {@link #registerBackend(DbBackend)} to use JPA entity resolution and entity graphs.
 */
@Slf4j
@SuppressWarnings("java:S100")
public class DatabaseSteps {

    public static final String TABLE_PATTERN = "([^ ]+)";

    public static boolean autoclean = true;
    public static List<String> schemasToClean = List.of("public");

    private static final List<DataSource> registeredDataSources = new CopyOnWriteArrayList<>();
    private static volatile DbBackend backend;
    private boolean disableTriggers = true;

    private final ObjectSteps objects;

    static {
        DynamicTransformers.register(InsertionMode.class, InsertionMode::parse);
    }

    public DatabaseSteps(ObjectSteps objects) {
        this.objects = objects;
    }

    /**
     * Register a DbBackend implementation.
     * Called by upper layers (e.g., JpaSteps) to override the default JDBC backend.
     */
    public static void registerBackend(DbBackend dbBackend) {
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
     * Thread-safe: uses synchronized block to prevent check-then-act race conditions.
     */
    public static synchronized void registerDataSource(DataSource dataSource) {
        if (!registeredDataSources.contains(dataSource)) {
            registeredDataSources.add(dataSource);
        }
    }

    /**
     * Clear all registered data sources (for test isolation).
     */
    public static void clearDataSources() {
        registeredDataSources.clear();
    }

    public static List<DataSource> getRegisteredDataSources() {
        return List.copyOf(registeredDataSources);
    }

    @Before(order = 100)
    public void before() {
        // Initialize default JdbcBackend if no upper layer registered a backend
        if (backend == null && !registeredDataSources.isEmpty()) {
            backend = new JdbcBackend(registeredDataSources.get(0));
        }
        if (autoclean) {
            registeredDataSources.forEach(dataSource -> {
                DatabaseCleaner.clean(dataSource, schemasToClean);
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.enable);
            });
        }
    }

    // ---- Table-level step definitions ----

    @Given(THAT + GUARD + "the " + TABLE_PATTERN + " table will contain" + INSERTION_MODE + ":$")
    public void the_table_will_contain(Guard guard, String table, InsertionMode insertionMode, Object content) {
        guard.in(objects, () -> {
            if (disableTriggers) {
                disableTriggersOnAllDataSources();
            }
            if (insertionMode == InsertionMode.ONLY) {
                backend.truncate(table);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List) Mapper.readAsAListOf(objects.resolve(content), Map.class);
            backend.insertRows(table, rows);
            if (disableTriggers) {
                enableTriggersOnAllDataSources();
            }
        });
    }

    @Then(THAT + GUARD + "the " + TABLE_PATTERN + " table (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_table_contains(Guard guard, String table, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> expectedRows = (List) Mapper.readAsAListOf(objects.resolve(content), Map.class);
            List<Map<String, Object>> actualRows = backend.queryAll(table, expectedRows);
            comparison.compare(actualRows, expectedRows);
        });
    }

    @Then(THAT + GUARD + "the " + TABLE_PATTERN + " table (?:still )?contains nothing$")
    public void the_table_contains_nothing(Guard guard, String table) {
        guard.in(objects, () -> assertThat(backend.count(table)).isZero());
    }

    @Then(THAT + GUARD + VARIABLE + " is the " + TABLE_PATTERN + " table content$")
    public void add_table_content_to_variable(Guard guard, String name, String table) {
        guard.in(objects, () -> objects.add(name, backend.queryAll(table, null)));
    }

    // ---- Trigger management ----

    @Given(THAT + GUARD + "the triggers are (enable|disable)d$")
    public void enable_triggers(Guard guard, String action) {
        guard.in(objects, () -> disableTriggers = action.equals("disable"));
    }

    public boolean isDisableTriggers() {
        return disableTriggers;
    }

    public void disableTriggersOnAllDataSources() {
        registeredDataSources.forEach(dataSource ->
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.disable));
    }

    public void enableTriggersOnAllDataSources() {
        registeredDataSources.forEach(dataSource ->
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.enable));
    }
}
