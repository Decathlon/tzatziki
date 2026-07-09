package com.decathlon.tzatziki.utils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import javax.sql.DataSource;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Plain JPA backend implementation that uses EntityManagerFactory directly.
 * No Spring dependencies — suitable for standalone JPA environments.
 * <p>
 * Creates fresh EntityManagers per operation and closes them after use
 * to prevent resource leaks and ensure thread safety.
 */
public class PlainJpaBackend implements JpaBackend {

    private final List<EntityManagerFactory> entityManagerFactories;
    private final List<DataSource> dataSources;
    private final Map<Class<?>, EntityManagerFactory> entityManagerFactoryByClass = new HashMap<>();
    private final Map<Class<?>, DataSource> dataSourceByClass = new HashMap<>();

    public PlainJpaBackend(EntityManagerFactory entityManagerFactory, DataSource dataSource) {
        this(List.of(entityManagerFactory), List.of(dataSource));
    }

    public PlainJpaBackend(List<EntityManagerFactory> entityManagerFactories, List<DataSource> dataSources) {
        this.entityManagerFactories = entityManagerFactories;
        this.dataSources = dataSources;
        initialize();
    }

    private void initialize() {
        entityManagerFactoryByClass.clear();
        dataSourceByClass.clear();

        for (int i = 0; i < entityManagerFactories.size(); i++) {
            EntityManagerFactory entityManagerFactory = entityManagerFactories.get(i);
            DataSource dataSource = dataSources.isEmpty() ? null : dataSources.get(Math.min(i, dataSources.size() - 1));
            try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
                entityManager.getMetamodel().getEntities().forEach(entityType -> {
                    Class<?> javaType = entityType.getJavaType();
                    entityManagerFactoryByClass.putIfAbsent(javaType, entityManagerFactory);
                    if (dataSource != null) {
                        dataSourceByClass.putIfAbsent(javaType, dataSource);
                    }
                });
            }
        }
    }

    private EntityManagerFactory getEntityManagerFactory(Class<?> entityClass) {
        EntityManagerFactory entityManagerFactory = entityManagerFactoryByClass.get(entityClass);
        if (entityManagerFactory == null) {
            throw new IllegalArgumentException("No EntityManagerFactory found for entity class: " + entityClass.getName());
        }
        return entityManagerFactory;
    }

    @Override
    public DataSource getDataSource(Class<?> entityClass) {
        DataSource dataSource = dataSourceByClass.get(entityClass);
        if (dataSource != null) {
            return dataSource;
        }
        return dataSources.isEmpty() ? null : dataSources.get(0);
    }

    @Override
    public List<DataSource> getAllDataSources() {
        return List.copyOf(dataSources);
    }

    @Override
    public Collection<Class<?>> getManagedEntityClasses() {
        return Set.copyOf(entityManagerFactoryByClass.keySet());
    }

    @Override
    public <E, R> R withEntityManager(Class<E> entityClass, Function<EntityManager, R> callback) {
        try (EntityManager entityManager = getEntityManagerFactory(entityClass).createEntityManager()) {
            return callback.apply(entityManager);
        }
    }

    @Override
    public <E> void withTransaction(Class<E> entityClass, Consumer<EntityManager> callback) {
        try (EntityManager entityManager = getEntityManagerFactory(entityClass).createEntityManager()) {
            EntityTransaction transaction = entityManager.getTransaction();
            transaction.begin();
            try {
                callback.accept(entityManager);
                transaction.commit();
            } catch (RuntimeException | Error e) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }
                throw e;
            }
        }
    }
}
