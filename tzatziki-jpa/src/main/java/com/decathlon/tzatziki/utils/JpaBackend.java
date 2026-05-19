package com.decathlon.tzatziki.utils;

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
import java.util.stream.Collectors;

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
     * Persist a list of entities.
     */
    default <E> void saveAll(Class<E> entityClass, List<E> entities) {
        withTransaction(entityClass, entityManager -> {
            for (E entity : entities) {
                entityManager.merge(entity);
            }
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
        if (tableWithSchema != null) {
            DataSource dataSource = getDataSource(entityClass);
            if (dataSource != null) {
                DatabaseCleaner.truncateTable(dataSource, tableWithSchema);
            }
        }
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
        LinkedHashMap<String, Type> entityClassByTableName = getManagedEntityClasses().stream()
                .sorted(this::compareEntityClasses)
                .map(clazz -> Map.entry(resolveTableName(clazz), (Type) clazz))
                .filter(entry -> hasText(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return Collections.unmodifiableMap(entityClassByTableName);
    }

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

    private int compareEntityClasses(Class<?> first, Class<?> second) {
        String defaultPackage = TypeParser.getDefaultPackage();
        if (defaultPackage == null) return 0;
        boolean firstInDefaultPackage = first.getPackageName().startsWith(defaultPackage);
        boolean secondInDefaultPackage = second.getPackageName().startsWith(defaultPackage);
        if (firstInDefaultPackage == secondInDefaultPackage) return 0;
        return firstInDefaultPackage ? -1 : 1;
    }

    private String resolveTableName(Class<?> clazz) {
        Annotation tableAnnotation = findTableAnnotation(clazz);
        if (tableAnnotation == null) return null;
        String tableName = readAnnotationAttribute(tableAnnotation, "name");
        if (!hasText(tableName)) {
            tableName = readAnnotationAttribute(tableAnnotation, "value");
        }
        return hasText(tableName) ? tableName : null;
    }

    private String resolveTableNameWithSchema(Class<?> clazz) {
        Annotation tableAnnotation = findTableAnnotation(clazz);
        if (tableAnnotation == null) return null;
        String tableName = resolveTableName(clazz);
        if (!hasText(tableName)) return null;
        String schemaName = readAnnotationAttribute(tableAnnotation, "schema");
        return hasText(schemaName) ? schemaName + "." + tableName : tableName;
    }

    private Annotation findTableAnnotation(Class<?> clazz) {
        Table jpaTable = clazz.getAnnotation(Table.class);
        if (jpaTable != null) {
            return jpaTable;
        }
        return Arrays.stream(clazz.getAnnotations())
                .filter(annotation -> annotation.annotationType().getName().equals("org.springframework.data.relational.core.mapping.Table"))
                .findFirst()
                .orElse(null);
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
