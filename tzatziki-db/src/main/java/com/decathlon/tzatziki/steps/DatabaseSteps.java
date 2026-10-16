package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.*;
import java.util.stream.Stream;

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
public class DatabaseSteps extends DatabaseConfiguration {

    public static final String TABLE_PATTERN = "([^ ]+)";

    private static final List<DataSource> registeredDataSources = new ArrayList<>();
    private static List<DataSource> backendDataSources = List.of();
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
     * Register a DbBackend implementation.
     * Called by upper layers (e.g., JpaSteps) to override the default JDBC backend.
     */
    public static void registerBackend(DbBackend dbBackend) {
        registerBackend(dbBackend, List.of());
    }

    /**
     * Replace the backend and its data sources, retaining sources explicitly registered by custom glue.
     */
    public static void registerBackend(DbBackend dbBackend, List<DataSource> dataSources) {
        backendDataSources = List.copyOf(dataSources);
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
        backendDataSources = List.of();
        backend = null;
    }

    public static List<DataSource> getRegisteredDataSources() {
        return Stream.concat(registeredDataSources.stream(), backendDataSources.stream()).distinct().toList();
    }

    @Before(order = 100)
    public void before() {
        initializeDefaultBackend();
        if (autoclean) {
            getRegisteredDataSources().forEach(dataSource -> {
                DatabaseCleaner.clean(dataSource, schemasToClean);
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.enable);
            });
        }
    }

    private static void initializeDefaultBackend() {
        List<DataSource> dataSources = getRegisteredDataSources();
        if (backend == null && !dataSources.isEmpty()) {
            backend = new JdbcBackend(dataSources.get(0));
        }
    }

    private static DbBackend requireBackend() {
        initializeDefaultBackend();
        if (backend == null) {
            throw new IllegalStateException("No DbBackend or DataSource registered. Call DatabaseSteps.registerDataSource(...) or DatabaseSteps.registerBackend(...) first.");
        }
        return backend;
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
        guard.in(objects, () -> objects.add(name, requireBackend().queryAll(table, null)));
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
            disableTriggersOnAllDataSources();
        }
        try {
            insertion.run();
        } finally {
            if (toggleTriggers) {
                enableTriggersOnAllDataSources();
            }
        }
    }

    public void disableTriggersOnAllDataSources() {
        getRegisteredDataSources().forEach(dataSource ->
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.disable));
    }

    public void enableTriggersOnAllDataSources() {
        getRegisteredDataSources().forEach(dataSource ->
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.enable));
    }
}
