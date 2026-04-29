package com.decathlon.tzatziki.utils;

import javax.sql.DataSource;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Backend interface that abstracts JPA entity operations.
 * <p>
 * Implementations may use pure JPA (PlainJpaBackend) or Spring Data (SpringJpaBackend).
 * This allows tzatziki-jpa Cucumber steps to work with any JPA environment.
 */
public interface JpaBackend {

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
}
