package com.decathlon.tzatziki.utils;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the defaults of {@link JpaBackend} for implementations that do not provide {@link EntityTableMapping}s.
 */
class JpaBackendTest {

    private final JpaBackend backend = new AnnotationOnlyBackend();

    @Test
    void resolvesTableNamesFromTheAnnotationsWhenNoMappingIsProvided() {
        assertThat(backend.getEntityClassByTableName())
                .containsEntry("custom_name", NamedEntity.class)
                .containsEntry("defaultnamed", DefaultNamed.class)
                .containsEntry("tables", WithTable.class)
                .containsEntry("archive.tables", WithTable.class)
                .containsEntry("archive.schemaonly", SchemaOnly.class);
        assertThat(backend.resolveEntityType("tables")).isEqualTo(WithTable.class);
        assertThat(backend.resolveEntityType("java.lang.String")).isEqualTo(String.class);
    }

    @Test
    void failsExplicitlyWithoutDataSource() {
        assertThatThrownBy(() -> backend.truncate(WithTable.class))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Cannot truncate table for entity " + WithTable.class.getName());
        assertThatThrownBy(() -> backend.insertRows("unknown_table", List.of(Map.of("id", 1))))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Cannot resolve entity class for table: unknown_table");
        assertThatThrownBy(() -> backend.contentOf("unknown table")).isInstanceOf(AssertionError.class);
    }

    @Entity(name = "custom_name")
    static class NamedEntity {
    }

    @Entity
    static class DefaultNamed {
    }

    @Entity
    @Table(name = "tables", schema = "archive")
    static class WithTable {
    }

    @Entity
    @Table(schema = "archive")
    static class SchemaOnly {
    }

    private static class AnnotationOnlyBackend implements JpaBackend {

        @Override
        public DataSource getDataSource(Class<?> entityClass) {
            return null;
        }

        @Override
        public List<DataSource> getAllDataSources() {
            return List.of();
        }

        @Override
        public Collection<Class<?>> getManagedEntityClasses() {
            return List.of(NamedEntity.class, DefaultNamed.class, WithTable.class, SchemaOnly.class);
        }

        @Override
        public <E, R> R withEntityManager(Class<E> entityClass, Function<EntityManager, R> callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <E> void withTransaction(Class<E> entityClass, Consumer<EntityManager> callback) {
            throw new UnsupportedOperationException();
        }
    }
}
