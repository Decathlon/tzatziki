package com.decathlon.tzatziki.utils;

import javax.sql.DataSource;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Backend interface that abstracts JPA entity operations.
 * <p>
 * Extends {@link DbBackend} so that every JPA backend also serves as the table-level
 * backend for {@link com.decathlon.tzatziki.steps.DatabaseSteps}.
 * Default implementations bridge table-name operations to entity-class operations
 * via {@link #resolveEntityType(String)} and JSON serialization, eliminating the need
 * for a separate adapter class.
 * <p>
 * Implementations may use pure JPA (PlainJpaBackend) or Spring Data (SpringJpaBackend).
 * This allows tzatziki-jpa Cucumber steps to work with any JPA environment.
 */
public interface JpaBackend extends DbBackend {

    /**
     * Find all entities of the given class, optionally optimizing fetches based on the
     * expected field structure passed by the caller.
     * <p>
     * Implementations own the persistence-context lifecycle for this query and must not
     * leak raw persistence objects back to callers.
     */
    <E> List<E> findAllWithExpectedFields(Class<E> entityClass, List<Map> expectedEntities);

    /**
     * Get the DataSource associated with the given entity class.
     */
    DataSource getDataSource(Class<?> entityClass);

    /**
     * Get all registered DataSources.
     */
    List<DataSource> getAllDataSources();

    /**
     * Persist a list of entities.
     */
    <E> void saveAll(Class<E> entityClass, List<E> entities);

    /**
     * Find all entities of the given class.
     */
    <E> List<E> findAll(Class<E> entityClass);

    /**
     * Count entities of the given type.
     */
    <E> long count(Class<E> entityClass);

    /**
     * Truncate the table for the given entity class.
     */
    <E> void truncate(Class<E> entityClass);

    /**
     * Resolve a table name or class name to a Java type.
     */
    Type resolveEntityType(String tableOrClassName);

    /**
     * Get all known entity types managed by this backend.
     */
    Map<String, Type> getEntityClassByTableName();

    // --- DbBackend default implementations (table-name → entity-class bridge) ---

    @Override
    @SuppressWarnings("unchecked")
    default void insertRows(String table, List<Map<String, Object>> rows) {
        Class<Object> entityClass = (Class<Object>) resolveEntityClass(table);
        List<Object> entities = rows.stream()
                .map(row -> Mapper.read(Mapper.toJson(row), entityClass))
                .toList();
        saveAll(entityClass, entities);
    }

    @Override
    @SuppressWarnings("unchecked")
    default List<Map<String, Object>> queryAll(String table, List<Map<String, Object>> expectedRows) {
        Class<Object> entityClass = (Class<Object>) resolveEntityClass(table);
        List<Map> expectedMaps = expectedRows == null ? null : (List<Map>) (List<?>) expectedRows;
        List<?> entities = findAllWithExpectedFields(entityClass, expectedMaps);
        Set<String> columns = new LinkedHashSet<>();
        if (expectedRows != null) {
            expectedRows.forEach(row -> columns.addAll(row.keySet()));
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Object entity : entities) {
            String json = Mapper.toNonDefaultJson(entity);
            Map<String, Object> map = Mapper.read(json, Map.class);
            if (!columns.isEmpty()) {
                Map<String, Object> filtered = new LinkedHashMap<>();
                for (String col : columns) {
                    if (map.containsKey(col)) {
                        filtered.put(col, map.get(col));
                    }
                }
                results.add(filtered);
            } else {
                results.add(map);
            }
        }
        return results;
    }

    @Override
    default long count(String table) {
        return count(resolveEntityClass(table));
    }

    @Override
    default void truncate(String table) {
        truncate(resolveEntityClass(table));
    }

    private Class<?> resolveEntityClass(String table) {
        Type type = resolveEntityType(table);
        if (type instanceof Class<?> clazz) return clazz;
        throw new AssertionError("Cannot resolve entity class for table: " + table);
    }
}
