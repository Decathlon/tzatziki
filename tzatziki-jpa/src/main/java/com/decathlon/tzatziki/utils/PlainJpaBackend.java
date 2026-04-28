package com.decathlon.tzatziki.utils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Table;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Plain JPA backend implementation that uses EntityManagerFactory directly.
 * No Spring dependencies — suitable for standalone JPA environments.
 */
@Slf4j
public class PlainJpaBackend implements JpaBackend {

    private final List<EntityManagerFactory> entityManagerFactories;
    private final List<DataSource> dataSources;
    private Map<Type, EntityManager> entityManagerByClass;
    private Map<String, Type> entityClassByTableName;

    public PlainJpaBackend(EntityManagerFactory entityManagerFactory, DataSource dataSource) {
        this(List.of(entityManagerFactory), List.of(dataSource));
    }

    public PlainJpaBackend(List<EntityManagerFactory> entityManagerFactories, List<DataSource> dataSources) {
        this.entityManagerFactories = entityManagerFactories;
        this.dataSources = dataSources;
        initialize();
    }

    private void initialize() {
        entityManagerByClass = new HashMap<>();
        entityClassByTableName = new HashMap<>();

        for (EntityManagerFactory emf : entityManagerFactories) {
            EntityManager em = emf.createEntityManager();
            em.getMetamodel().getEntities().forEach(entityType -> {
                Class<?> javaType = entityType.getJavaType();
                entityManagerByClass.putIfAbsent(javaType, em);
                String tableName = getTableName(javaType);
                if (tableName != null) {
                    entityClassByTableName.putIfAbsent(tableName, javaType);
                }
            });
        }
    }

    @Override
    public EntityManager getEntityManager(Class<?> entityClass) {
        EntityManager em = entityManagerByClass.get(entityClass);
        if (em == null) {
            throw new IllegalArgumentException("No EntityManager found for entity class: " + entityClass.getName());
        }
        return em;
    }

    @Override
    public DataSource getDataSource(Class<?> entityClass) {
        // Simple mapping: first data source if only one, otherwise try to match via EMF index
        if (dataSources.size() == 1) {
            return dataSources.get(0);
        }
        for (int i = 0; i < entityManagerFactories.size(); i++) {
            EntityManagerFactory emf = entityManagerFactories.get(i);
            EntityManager em = emf.createEntityManager();
            try {
                if (em.getMetamodel().getEntities().stream()
                        .anyMatch(e -> e.getJavaType().equals(entityClass))) {
                    return dataSources.get(Math.min(i, dataSources.size() - 1));
                }
            } finally {
                em.close();
            }
        }
        return dataSources.get(0);
    }

    @Override
    public List<DataSource> getAllDataSources() {
        return List.copyOf(dataSources);
    }

    @Override
    public <E> void saveAll(Class<E> entityClass, List<E> entities) {
        EntityManager em = getEntityManager(entityClass);
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            for (E entity : entities) {
                em.persist(entity);
            }
            em.flush();
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    @Override
    public <E> List<E> findAll(Class<E> entityClass) {
        EntityManager em = getEntityManager(entityClass);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<E> query = cb.createQuery(entityClass);
        Root<E> root = query.from(entityClass);
        query.select(root);
        return em.createQuery(query).getResultList();
    }

    @Override
    public <E> long count(Class<E> entityClass) {
        EntityManager em = getEntityManager(entityClass);
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        query.select(cb.count(query.from(entityClass)));
        return em.createQuery(query).getSingleResult();
    }

    @Override
    @SneakyThrows
    public <E> void truncate(Class<E> entityClass) {
        String tableWithSchema = getTableNameWithSchema(entityClass);
        if (tableWithSchema != null) {
            DataSource ds = getDataSource(entityClass);
            DatabaseCleaner.truncateTable(ds, tableWithSchema);
        }
    }

    @Override
    public Type resolveEntityType(String tableOrClassName) {
        Type type = entityClassByTableName.get(tableOrClassName);
        if (type != null) return type;
        return TypeParser.parse(tableOrClassName);
    }

    @Override
    public Map<String, Type> getEntityClassByTableName() {
        return Collections.unmodifiableMap(entityClassByTableName);
    }

    private String getTableName(Class<?> clazz) {
        Table tableAnnotation = clazz.getAnnotation(Table.class);
        if (tableAnnotation != null) {
            String name = tableAnnotation.name();
            if (StringUtils.isNotBlank(name)) return name;
        }
        return null;
    }

    private String getTableNameWithSchema(Class<?> clazz) {
        Table tableAnnotation = clazz.getAnnotation(Table.class);
        if (tableAnnotation == null) return null;
        String name = tableAnnotation.name();
        String schema = tableAnnotation.schema();
        if (StringUtils.isBlank(name)) return null;
        return StringUtils.isNotBlank(schema) ? schema + "." + name : name;
    }
}
