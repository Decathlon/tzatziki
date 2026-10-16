package com.decathlon.tzatziki.utils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Table;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.jpa.SpecHints;

import javax.sql.DataSource;
import java.lang.reflect.Type;
import java.util.*;

/**
 * JPA backend working with plain {@link EntityManagerFactory}s, without Spring.
 * <p>
 * Also serves as the table-level {@link DbBackend} of {@link com.decathlon.tzatziki.steps.DatabaseSteps}:
 * tables are resolved to entities through their {@code @Table} name, and tables without entity
 * (join tables...) are handled through plain JDBC on the first DataSource.
 */
@Slf4j
public class JpaBackend implements DbBackend {

    private final Map<EntityManagerFactory, DataSource> dataSourceByEntityManagerFactory;
    private final Map<Class<?>, EntityManagerFactory> entityManagerFactoryByClass = new LinkedHashMap<>();
    private Map<String, Class<?>> entityClassByTableName;

    public JpaBackend(EntityManagerFactory entityManagerFactory, DataSource dataSource) {
        this(Map.of(entityManagerFactory, dataSource));
    }

    public JpaBackend(Map<EntityManagerFactory, DataSource> dataSourceByEntityManagerFactory) {
        this.dataSourceByEntityManagerFactory = new LinkedHashMap<>(dataSourceByEntityManagerFactory);
        dataSourceByEntityManagerFactory.keySet().forEach(entityManagerFactory -> entityManagerFactory.getMetamodel().getEntities()
                .forEach(entityType -> entityManagerFactoryByClass.putIfAbsent(entityType.getJavaType(), entityManagerFactory)));
    }

    // ---- entity operations ----

    public <E> void saveAll(Class<E> entityClass, List<E> entities) {
        try (EntityManager entityManager = entityManagerFactory(entityClass).createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            transaction.begin();
            try {
                entities.forEach(entityManager::merge);
                transaction.commit();
            } finally {
                if (transaction.isActive()) transaction.rollback();
            }
        }
    }

    public <E> List<E> findAll(Class<E> entityClass) {
        return findAll(entityClass, List.of());
    }

    /**
     * Finds all the entities, loading only the fields present in the expected entities.
     */
    public <E> List<E> findAll(Class<E> entityClass, List<Map> expectedEntities) {
        try (EntityManager entityManager = entityManager(entityClass)) {
            CriteriaQuery<E> query = entityManager.getCriteriaBuilder().createQuery(entityClass);
            query.select(query.from(entityClass));
            TypedQuery<E> typedQuery = entityManager.createQuery(query);
            if (expectedEntities != null && !expectedEntities.isEmpty()) {
                try {
                    typedQuery.setHint(SpecHints.HINT_SPEC_FETCH_GRAPH, EntityGraphUtils.createEntityGraph(entityManager, entityClass, expectedEntities));
                } catch (Exception e) {
                    log.debug("Could not get entity graph for {}, loading all fields", entityClass, e);
                }
            }
            return typedQuery.getResultList();
        }
    }

    public long count(Class<?> entityClass) {
        try (EntityManager entityManager = entityManager(entityClass)) {
            CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = criteriaBuilder.createQuery(Long.class);
            query.select(criteriaBuilder.count(query.from(entityClass)));
            return entityManager.createQuery(query).getSingleResult();
        }
    }

    public void truncate(Class<?> entityClass) {
        String table = qualifiedTableName(entityClass);
        if (table == null) {
            throw new AssertionError("Cannot truncate table for entity " + entityClass.getName() + ": no @Table annotation");
        }
        DatabaseCleaner.truncateTable(getDataSource(entityClass), table);
    }

    /**
     * Resolves a table name (optionally prefixed with its schema) or a class name to a type.
     */
    public Type resolveEntityType(String tableOrClassName) {
        Class<?> entityClass = entityClassByTableName().get(tableOrClassName);
        return entityClass != null ? entityClass : TypeParser.parse(tableOrClassName);
    }

    public Collection<Class<?>> getManagedEntityClasses() {
        return Collections.unmodifiableSet(entityManagerFactoryByClass.keySet());
    }

    public List<DataSource> getAllDataSources() {
        return dataSourceByEntityManagerFactory.values().stream().filter(Objects::nonNull).distinct().toList();
    }

    public DataSource getDataSource(Class<?> entityClass) {
        DataSource dataSource = dataSourceByEntityManagerFactory.get(entityManagerFactoryByClass.get(entityClass));
        if (dataSource != null) return dataSource;
        return getAllDataSources().stream().findFirst()
                .orElseThrow(() -> new AssertionError("No DataSource registered for entity " + entityClass.getName()));
    }

    // ---- hooks ----

    /**
     * @return an EntityManager for the given entity class, closed after each operation
     */
    protected EntityManager entityManager(Class<?> entityClass) {
        return entityManagerFactory(entityClass).createEntityManager();
    }

    /**
     * @return the table name of the entity, prefixed with its schema if any, or null if it has no {@code @Table} name
     */
    protected String qualifiedTableName(Class<?> entityClass) {
        Table table = entityClass.getAnnotation(Table.class);
        if (table == null || table.name().isBlank()) return null;
        return table.schema().isBlank() ? table.name() : table.schema() + "." + table.name();
    }

    protected EntityManagerFactory entityManagerFactory(Class<?> entityClass) {
        EntityManagerFactory entityManagerFactory = entityManagerFactoryByClass.get(entityClass);
        if (entityManagerFactory == null) {
            throw new AssertionError(entityClass + " is not an Entity!");
        }
        return entityManagerFactory;
    }

    // ---- DbBackend: table-name → entity-class bridge ----

    @Override
    @SuppressWarnings("unchecked")
    public void insertRows(String table, List<Map<String, Object>> rows) {
        Class<Object> entityClass = (Class<Object>) entityClassOf(table);
        if (entityClass == null) {
            jdbc(table).insertRows(table, rows);
            return;
        }
        saveAll(entityClass, rows.stream().map(row -> Mapper.read(Mapper.toJson(row), entityClass)).toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> queryAll(String table, List<Map<String, Object>> expectedRows) {
        Class<?> entityClass = entityClassOf(table);
        if (entityClass == null) {
            return jdbc(table).queryAll(table, expectedRows);
        }
        // full JSON: default values (false, 0, null) must remain assertable
        return findAll(entityClass, (List<Map>) (List<?>) expectedRows).stream()
                .map(entity -> (Map<String, Object>) Mapper.read(Mapper.toJson(entity), Map.class))
                .toList();
    }

    /**
     * Returns the entities themselves for entity-mapped tables, so that variables keep their Java types.
     */
    @Override
    public List<?> contentOf(String table) {
        Class<?> entityClass = entityClassOf(table);
        return entityClass == null ? jdbc(table).queryAll(table, null) : findAll(entityClass);
    }

    @Override
    public long count(String table) {
        Class<?> entityClass = entityClassOf(table);
        return entityClass == null ? jdbc(table).count(table) : count(entityClass);
    }

    @Override
    public void truncate(String table) {
        Class<?> entityClass = entityClassOf(table);
        if (entityClass == null) {
            jdbc(table).truncate(table);
        } else {
            truncate(entityClass);
        }
    }

    /**
     * @return the managed entity class of the given table or class name, or null (e.g. join tables)
     */
    private Class<?> entityClassOf(String table) {
        Class<?> entityClass = entityClassByTableName().get(table);
        if (entityClass != null) return entityClass;
        try {
            return TypeParser.parse(table) instanceof Class<?> clazz && getManagedEntityClasses().contains(clazz) ? clazz : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private DbBackend jdbc(String table) {
        return getAllDataSources().stream().findFirst().<DbBackend>map(JdbcBackend::new)
                .orElseThrow(() -> new AssertionError("Cannot resolve entity class for table: " + table));
    }

    private Map<String, Class<?>> entityClassByTableName() {
        if (entityClassByTableName == null) {
            Map<String, Class<?>> map = new HashMap<>();
            // entities of the default TypeParser package win over homonymous tables
            String defaultPackage = Objects.requireNonNullElse(TypeParser.getDefaultPackage(), "");
            getManagedEntityClasses().stream()
                    .sorted(Comparator.comparing(clazz -> !clazz.getPackageName().startsWith(defaultPackage)))
                    .forEach(clazz -> {
                        String qualifiedTableName = qualifiedTableName(clazz);
                        if (qualifiedTableName != null) {
                            map.putIfAbsent(qualifiedTableName, clazz);
                            map.putIfAbsent(qualifiedTableName.substring(qualifiedTableName.indexOf('.') + 1), clazz);
                        }
                    });
            entityClassByTableName = map;
        }
        return entityClassByTableName;
    }
}
