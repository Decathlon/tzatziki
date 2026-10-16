package com.decathlon.tzatziki.utils;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;

import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Backend interface that abstracts JPA entity operations.
 * <p>
 * Extends {@link DbBackend} so that every JPA backend also serves as the table-level
 * backend for {@link com.decathlon.tzatziki.steps.DatabaseSteps}.
 * Default implementations bridge table-name operations to entity-class operations
 * and centralize the common EntityManager-based CRUD/query/count logic shared by
 * the plain JPA and Spring-backed implementations.
 * <p>
 * Implementations may use pure JPA (PlainJpaBackend) or Spring Data (SpringJpaBackend).
 * This allows tzatziki-jpa Cucumber steps to work with any JPA environment.
 */
public interface JpaBackend extends DbBackend {

    /**
     * Get the DataSource associated with the given entity class.
     */
    DataSource getDataSource(Class<?> entityClass);

    /**
     * Get all registered DataSources.
     */
    List<DataSource> getAllDataSources();

    /**
     * Get all managed entity classes known by this backend.
     */
    Collection<Class<?>> getManagedEntityClasses();

    /**
     * Execute a callback with an EntityManager suitable for the given entity class.
     * Implementations own the EntityManager lifecycle.
     */
    <E, R> R withEntityManager(Class<E> entityClass, Function<EntityManager, R> callback);

    /**
     * Execute a callback within a transaction suitable for the given entity class.
     * Implementations own both the transaction strategy and the EntityManager lifecycle.
     */
    <E> void withTransaction(Class<E> entityClass, Consumer<EntityManager> callback);

    /**
     * Physical table mapping of a managed JPA entity as resolved by the persistence provider
     * (see {@link EntityTableMapping#of}). Implementations are expected to compute it once per entity class.
     *
     * @return the mapping, or null if unknown (e.g. non-JPA Spring Data entity), in which case the
     * {@code @Table}/{@code @Entity} annotations are used to resolve the table name
     */
    default EntityTableMapping getTableMapping(Class<?> entityClass) {
        return null;
    }

    /**
     * Persist a list of entities.
     */
    default <E> void saveAll(Class<E> entityClass, List<E> entities) {
        withTransaction(entityClass, entityManager -> {
            entities.forEach(entityManager::merge);
            entityManager.flush();
        });
    }

    /**
     * Find all entities of the given class.
     */
    default <E> List<E> findAll(Class<E> entityClass) {
        return findAllWithExpectedFields(entityClass, List.of());
    }

    /**
     * Find all entities of the given class, optionally optimizing fetches based on the
     * expected field structure passed by the caller.
     */
    default <E> List<E> findAllWithExpectedFields(Class<E> entityClass, List<Map> expectedEntities) {
        return withEntityManager(entityClass, entityManager -> JpaQueryUtils.findAll(entityManager, entityClass, expectedEntities));
    }

    /**
     * Count entities of the given type.
     */
    default <E> long count(Class<E> entityClass) {
        return withEntityManager(entityClass, entityManager -> {
            CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
            query.select(criteriaBuilder.count(query.from(entityClass)));
            return entityManager.createQuery(query).getSingleResult();
        });
    }

    /**
     * Truncate the table for the given entity class.
     */
    default <E> void truncate(Class<E> entityClass) {
        String tableWithSchema = resolveTableNameWithSchema(entityClass);
        DataSource dataSource = getDataSource(entityClass);
        if (tableWithSchema == null || dataSource == null) {
            throw new AssertionError("Cannot truncate table for entity " + entityClass.getName());
        }
        DatabaseCleaner.truncateTable(dataSource, tableWithSchema);
    }

    /**
     * Resolve a table name or class name to a Java type.
     */
    default Type resolveEntityType(String tableOrClassName) {
        Type type = getEntityClassByTableName().get(tableOrClassName);
        if (type != null) return type;
        return TypeParser.parse(tableOrClassName);
    }

    /**
     * Get all known entity types managed by this backend.
     */
    default Map<String, Type> getEntityClassByTableName() {
        LinkedHashMap<String, Type> entityClassByTableName = new LinkedHashMap<>();
        getManagedEntityClasses().stream()
                .sorted(this::compareEntityClasses)
                .forEach(clazz -> {
                    String tableName = resolveTableName(clazz);
                    if (hasText(tableName)) {
                        entityClassByTableName.putIfAbsent(tableName, clazz);
                    }
                    String tableNameWithSchema = resolveTableNameWithSchema(clazz);
                    if (hasText(tableNameWithSchema)) {
                        entityClassByTableName.putIfAbsent(tableNameWithSchema, clazz);
                    }
                });
        return Collections.unmodifiableMap(entityClassByTableName);
    }

    // --- DbBackend default implementations (table-name → entity-class bridge) ---

    @Override
    @SuppressWarnings("unchecked")
    default void insertRows(String table, List<Map<String, Object>> rows) {
        Class<Object> entityClass = (Class<Object>) findEntityClass(table);
        if (entityClass == null) {
            jdbcFallback(table).insertRows(table, rows);
            return;
        }
        EntityTableMapping mapping = getTableMapping(entityClass);
        List<Object> entities = rows.stream()
                .map(row -> mapping == null ? row : mapping.withPropertyKeys(row))
                .map(row -> Mapper.read(Mapper.toJson(row), entityClass))
                .toList();
        saveAll(entityClass, entities);
    }

    @Override
    @SuppressWarnings("unchecked")
    default List<Map<String, Object>> queryAll(String table, List<Map<String, Object>> expectedRows) {
        Class<Object> entityClass = (Class<Object>) findEntityClass(table);
        if (entityClass == null) {
            return jdbcFallback(table).queryAll(table, expectedRows);
        }
        EntityTableMapping mapping = getTableMapping(entityClass);
        List<Map<String, Object>> expectedEntities = expectedRows == null || mapping == null
                ? expectedRows
                : expectedRows.stream().map(mapping::withPropertyKeys).toList();
        // column names used in the expected rows are exposed as aliases of their property in the actual rows
        Map<String, String> propertyByColumnAlias = new LinkedHashMap<>();
        if (expectedRows != null && mapping != null) {
            expectedRows.forEach(row -> row.keySet().forEach(key -> {
                String property = mapping.toProperty(key);
                if (!property.equals(key)) propertyByColumnAlias.put(key, property);
            }));
        }
        return findAllWithExpectedFields(entityClass, (List<Map>) (List<?>) expectedEntities).stream()
                // full JSON: default values (false, 0, null) must remain assertable
                .<Map<String, Object>>map(entity -> {
                    Map<String, Object> row = new LinkedHashMap<>(Mapper.read(Mapper.toJson(entity), Map.class));
                    propertyByColumnAlias.forEach((column, property) -> row.putIfAbsent(column, row.get(property)));
                    return row;
                })
                .toList();
    }

    /**
     * Returns the entities themselves for entity-mapped tables, so that variables keep their Java types.
     */
    @Override
    default List<?> contentOf(String table) {
        Class<?> entityClass = findEntityClass(table);
        return entityClass == null ? jdbcFallback(table).queryAll(table, null) : findAll(entityClass);
    }

    @Override
    default long count(String table) {
        Class<?> entityClass = findEntityClass(table);
        return entityClass == null ? jdbcFallback(table).count(table) : count(entityClass);
    }

    @Override
    default void truncate(String table) {
        Class<?> entityClass = findEntityClass(table);
        if (entityClass == null) {
            jdbcFallback(table).truncate(table);
        } else {
            truncate(entityClass);
        }
    }

    /**
     * @return the managed entity class mapped to the given table or class name, or null if there is none
     * (e.g. join tables or tables without entity), in which case table operations fall back to plain JDBC.
     */
    private Class<?> findEntityClass(String table) {
        Type type = getEntityClassByTableName().get(table);
        if (type == null) {
            try {
                type = TypeParser.parse(table);
            } catch (RuntimeException e) {
                return null;
            }
        }
        return type instanceof Class<?> clazz && getManagedEntityClasses().contains(clazz) ? clazz : null;
    }

    private DbBackend jdbcFallback(String table) {
        return JdbcFallback.forTable(getAllDataSources(), table);
    }

    private int compareEntityClasses(Class<?> first, Class<?> second) {
        String defaultPackage = TypeParser.getDefaultPackage();
        if (defaultPackage == null) return 0;
        boolean firstInDefaultPackage = first.getPackageName().startsWith(defaultPackage);
        boolean secondInDefaultPackage = second.getPackageName().startsWith(defaultPackage);
        if (firstInDefaultPackage == secondInDefaultPackage) return 0;
        return firstInDefaultPackage ? -1 : 1;
    }

    private String resolveTableName(Class<?> clazz) {
        EntityTableMapping mapping = getTableMapping(clazz);
        if (mapping != null) {
            return mapping.table();
        }
        Annotation tableAnnotation = findTableAnnotation(clazz);
        if (tableAnnotation != null) {
            String tableName = readAnnotationAttribute(tableAnnotation, "name");
            if (!hasText(tableName)) {
                tableName = readAnnotationAttribute(tableAnnotation, "value");
            }
            if (hasText(tableName)) {
                return tableName;
            }
        }
        Entity entityAnnotation = clazz.getAnnotation(Entity.class);
        if (entityAnnotation != null) {
            return hasText(entityAnnotation.name())
                    ? entityAnnotation.name()
                    : clazz.getSimpleName().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private String resolveTableNameWithSchema(Class<?> clazz) {
        EntityTableMapping mapping = getTableMapping(clazz);
        if (mapping != null) {
            return mapping.qualifiedTable();
        }
        String tableName = resolveTableName(clazz);
        if (!hasText(tableName)) return null;
        Annotation tableAnnotation = findTableAnnotation(clazz);
        String schemaName = tableAnnotation == null ? null : readAnnotationAttribute(tableAnnotation, "schema");
        return hasText(schemaName) ? schemaName + "." + tableName : tableName;
    }

    private Annotation findTableAnnotation(Class<?> clazz) {
        Table jpaTable = clazz.getAnnotation(Table.class);
        if (jpaTable != null) {
            return jpaTable;
        }
        Class<? extends Annotation> springDataTable = SpringDataTable.ANNOTATION;
        return springDataTable == null ? null : clazz.getAnnotation(springDataTable);
    }

    private String readAnnotationAttribute(Annotation annotation, String attributeName) {
        try {
            Method method = annotation.annotationType().getMethod(attributeName);
            Object value = method.invoke(annotation);
            return value instanceof String stringValue ? stringValue : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot read @" + annotation.annotationType().getSimpleName() + "." + attributeName, e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
