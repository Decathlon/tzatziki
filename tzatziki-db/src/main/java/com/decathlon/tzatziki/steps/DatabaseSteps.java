package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.DatabaseCleaner;
import com.decathlon.tzatziki.utils.Guard;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.THAT;

@Slf4j
public class DatabaseSteps {

    public static boolean autoclean = true;
    public static List<String> schemasToClean = List.of("public");

    private static final List<DataSource> registeredDataSources = new ArrayList<>();
    private boolean disableTriggers = true;

    private final ObjectSteps objects;

    public DatabaseSteps(ObjectSteps objects) {
        this.objects = objects;
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
    }

    public static List<DataSource> getRegisteredDataSources() {
        return List.copyOf(registeredDataSources);
    }

    @Before(order = 100)
    public void before() {
        if (autoclean) {
            registeredDataSources.forEach(dataSource -> {
                DatabaseCleaner.clean(dataSource, schemasToClean);
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.enable);
            });
        }
    }

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
