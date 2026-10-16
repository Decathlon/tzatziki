package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.DbBackend;
import com.decathlon.tzatziki.utils.Guard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseStepsTest {

    private List<DataSource> savedDataSources;
    private DbBackend savedBackend;
    private ObjectSteps objects;
    private DatabaseSteps steps;

    @BeforeEach
    void saveStaticState() {
        // DatabaseSteps holds static state shared with the Cucumber tests running in the same JVM
        savedDataSources = new ArrayList<>(DatabaseSteps.getRegisteredDataSources());
        savedBackend = DatabaseSteps.getBackend();
        DatabaseSteps.clearDataSources();
        objects = new ObjectSteps();
        steps = new DatabaseSteps(objects);
    }

    @AfterEach
    void restoreStaticState() {
        DatabaseSteps.clearDataSources();
        if (savedBackend != null) {
            DatabaseSteps.registerBackend(savedBackend, List.of());
        }
        savedDataSources.forEach(DatabaseSteps::registerDataSource);
    }

    @Test
    void failsWithAnExplicitMessageWhenNothingIsRegistered() {
        assertThat(DatabaseSteps.getBackend()).isNull();
        Guard always = Guard.always();
        assertThatThrownBy(() -> steps.the_table_contains_nothing(always, "users"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No DbBackend or DataSource registered");
    }

    @Test
    void registeredBackendIsUsedAndItsDataSourcesAreManaged() {
        DataSource registered = new PGSimpleDataSource();
        DataSource backendOwned = new PGSimpleDataSource();
        DbBackend backend = new InMemoryBackend();
        DatabaseSteps.registerDataSource(new PGSimpleDataSource());
        DatabaseSteps.registerBackend(backend, List.of(backendOwned));
        DatabaseSteps.registerDataSource(registered);
        DatabaseSteps.registerDataSource(registered);

        assertThat(DatabaseSteps.getBackend()).isSameAs(backend);
        assertThat(DatabaseSteps.getRegisteredDataSources()).containsExactly(backendOwned, registered);
    }

    @Test
    void tableContentVariableUsesTheBackendContent() {
        DatabaseSteps.registerBackend(new InMemoryBackend(), List.of());

        steps.add_table_content_to_variable(Guard.always(), "content", "users");

        assertThat(objects.<List<Object>>get("content")).containsExactly("entity of users");
    }

    @Test
    void triggersAreDisabledByDefaultAndCanBeEnabled() {
        assertThat(steps.isDisableTriggers()).isTrue();
        steps.enable_triggers(Guard.always(), "enable");
        assertThat(steps.isDisableTriggers()).isFalse();
    }

    private static class InMemoryBackend implements DbBackend {

        @Override
        public void insertRows(String table, List<Map<String, Object>> rows) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Map<String, Object>> queryAll(String table, List<Map<String, Object>> expectedRows) {
            return List.of();
        }

        @Override
        public List<Object> contentOf(String table) {
            return List.of("entity of " + table);
        }

        @Override
        public long count(String table) {
            return 0;
        }

        @Override
        public void truncate(String table) {
            throw new UnsupportedOperationException();
        }
    }
}
