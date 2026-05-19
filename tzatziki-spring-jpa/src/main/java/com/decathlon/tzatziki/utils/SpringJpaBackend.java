package com.decathlon.tzatziki.utils;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.data.repository.CrudRepository;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Spring-based JPA backend that shares the common EntityManager-driven CRUD logic
 * from {@link JpaBackend} for JPA-managed entities, while retaining Spring-only
 * repository helpers (and repository fallback for non-JPA Spring Data entities)
 * for repository-typed and ordered-query step definitions.
 */
@Slf4j
public class SpringJpaBackend implements JpaBackend {

    private final ApplicationContext applicationContext;
    private final List<LocalContainerEntityManagerFactoryBean> entityManagerFactories;
    private final List<EntityManager> entityManagers;
    private volatile Map<Class<?>, CrudRepository<?, ?>> crudRepositoryByClass;
    private volatile Map<Class<?>, EntityManagerFactory> entityManagerFactoryByClass;
    private volatile Map<Class<?>, DataSource> dataSourceByClass;
    private volatile Map<Class<?>, EntityManager> sharedEntityManagerByClass;

    public SpringJpaBackend(ApplicationContext applicationContext,
                            @Nullable List<LocalContainerEntityManagerFactoryBean> entityManagerFactories,
                            @Nullable List<EntityManager> entityManagers) {
        this.applicationContext = applicationContext;
        this.entityManagerFactories = entityManagerFactories;
        this.entityManagers = entityManagers;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void initialize() {
        if (crudRepositoryByClass == null) {
            crudRepositoryByClass = applicationContext.getBeansOfType(CrudRepository.class).values()
                    .stream()
                    .map(crudRepository -> Map.entry(crudRepository, TypeUtils.getTypeArguments(crudRepository.getClass(), CrudRepository.class).get(CrudRepository.class.getTypeParameters()[0])))
                    .sorted((first, second) -> {
                        if (first.getValue() instanceof Class) return -1;
                        return second.getValue() instanceof Class ? 1 : 0;
                    })
                    .<Map.Entry<Class<?>, CrudRepository<?, ?>>>mapMulti((crudRepositoryWithType, consumer) -> {
                        CrudRepository<?, ?> crudRepository = crudRepositoryWithType.getKey();
                        Type type = crudRepositoryWithType.getValue();
                        if (type instanceof TypeVariable<?> typeVariable) {
                            type = typeVariable.getBounds()[0];
                            TypeParser.getSubtypesOf((Class<?>) type)
                                    .forEach(clazz -> consumer.accept(Map.entry(clazz, crudRepository)));
                        }
                        if (type instanceof Class<?> clazz) consumer.accept(Map.entry(clazz, crudRepository));
                    })
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (first, second) -> first,
                            LinkedHashMap::new
                    ));
        }

        if (entityManagerFactoryByClass == null) {
            Map<Class<?>, EntityManagerFactory> factoriesByClass = new HashMap<>();
            Map<Class<?>, DataSource> dataSourcesByClass = new HashMap<>();
            if (entityManagerFactories != null) {
                for (LocalContainerEntityManagerFactoryBean entityManagerFactoryBean : entityManagerFactories) {
                    EntityManagerFactory entityManagerFactory = Optional.ofNullable(entityManagerFactoryBean.getNativeEntityManagerFactory())
                            .orElseGet(entityManagerFactoryBean::getObject);
                    if (entityManagerFactory == null) {
                        continue;
                    }
                    DataSource dataSource = entityManagerFactoryBean.getDataSource();
                    try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
                        entityManager.getMetamodel().getEntities().forEach(entityType -> {
                            Class<?> javaType = entityType.getJavaType();
                            factoriesByClass.putIfAbsent(javaType, entityManagerFactory);
                            if (dataSource != null) {
                                dataSourcesByClass.putIfAbsent(javaType, dataSource);
                            }
                        });
                    }
                }
            }
            entityManagerFactoryByClass = factoriesByClass;
            dataSourceByClass = dataSourcesByClass;
        }

        if (sharedEntityManagerByClass == null) {
            Map<Class<?>, EntityManager> entityManagersByClass = new HashMap<>();
            if (entityManagers != null) {
                entityManagers.stream()
                        .flatMap(entityManager -> entityManager.getMetamodel().getEntities().stream()
                                .map(entityType -> Map.entry(entityType.getJavaType(), entityManager)))
                        .forEach(entry -> entityManagersByClass.putIfAbsent(entry.getKey(), entry.getValue()));
            }
            sharedEntityManagerByClass = entityManagersByClass;
        }
    }

    @Override
    public DataSource getDataSource(Class<?> entityClass) {
        DataSource dataSource = dataSourceByClass.get(entityClass);
        if (dataSource != null) {
            return dataSource;
        }
        if (entityManagerFactories == null || entityManagerFactories.isEmpty()) {
            return null;
        }
        return entityManagerFactories.get(0).getDataSource();
    }

    @Override
    public List<DataSource> getAllDataSources() {
        if (entityManagerFactories == null) return List.of();
        return entityManagerFactories.stream()
                .map(LocalContainerEntityManagerFactoryBean::getDataSource)
                .toList();
    }

    @Override
    public Collection<Class<?>> getManagedEntityClasses() {
        LinkedHashSet<Class<?>> managedEntityClasses = new LinkedHashSet<>();
        if (entityManagerFactoryByClass != null) {
            managedEntityClasses.addAll(entityManagerFactoryByClass.keySet());
        }
        if (sharedEntityManagerByClass != null) {
            managedEntityClasses.addAll(sharedEntityManagerByClass.keySet());
        }
        if (crudRepositoryByClass != null) {
            managedEntityClasses.addAll(crudRepositoryByClass.keySet());
        }
        return List.copyOf(managedEntityClasses);
    }

    @Override
    public <E, R> R withEntityManager(Class<E> entityClass, Function<EntityManager, R> callback) {
        EntityManagerFactory entityManagerFactory = entityManagerFactoryByClass.get(entityClass);
        if (entityManagerFactory != null) {
            try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
                return callback.apply(entityManager);
            }
        }
        EntityManager entityManager = sharedEntityManagerByClass.get(entityClass);
        if (entityManager != null) {
            return callback.apply(entityManager);
        }
        throw new AssertionError(entityClass + " is not an Entity!");
    }

    @Override
    public <E> void withTransaction(Class<E> entityClass, Consumer<EntityManager> callback) {
        EntityManagerFactory entityManagerFactory = entityManagerFactoryByClass.get(entityClass);
        if (entityManagerFactory != null) {
            try (EntityManager entityManager = entityManagerFactory.createEntityManager()) {
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
            return;
        }

        EntityManager entityManager = sharedEntityManagerByClass.get(entityClass);
        if (entityManager == null) {
            throw new AssertionError(entityClass + " is not an Entity!");
        }

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

    @Override
    @SuppressWarnings("unchecked")
    public <E> void saveAll(Class<E> entityClass, List<E> entities) {
        if (hasEntityManagerSupport(entityClass)) {
            JpaBackend.super.saveAll(entityClass, entities);
            return;
        }
        CrudRepository<E, ?> repository = (CrudRepository<E, ?>) crudRepositoryByClass.get(entityClass);
        if (repository == null) throw new AssertionError(entityClass + " is not an Entity!");
        repository.saveAll(entities);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> List<E> findAll(Class<E> entityClass) {
        if (hasEntityManagerSupport(entityClass)) {
            return JpaBackend.super.findAll(entityClass);
        }
        CrudRepository<E, ?> repository = (CrudRepository<E, ?>) crudRepositoryByClass.get(entityClass);
        if (repository == null) throw new AssertionError(entityClass + " is not an Entity!");
        return StreamSupport.stream(repository.findAll().spliterator(), false).toList();
    }

    @Override
    public <E> List<E> findAllWithExpectedFields(Class<E> entityClass, List<Map> expectedEntities) {
        if (hasEntityManagerSupport(entityClass)) {
            return JpaBackend.super.findAllWithExpectedFields(entityClass, expectedEntities);
        }
        return findAll(entityClass);
    }

    @Override
    public <E> long count(Class<E> entityClass) {
        if (hasEntityManagerSupport(entityClass)) {
            return JpaBackend.super.count(entityClass);
        }
        CrudRepository<?, ?> repository = crudRepositoryByClass.get(entityClass);
        if (repository == null) throw new AssertionError(entityClass + " is not an Entity!");
        return repository.count();
    }

    // --- Spring-specific helpers ---

    @SuppressWarnings("unchecked")
    public <E> CrudRepository<E, ?> getRepositoryForEntity(Type type) {
        CrudRepository<?, ?> crudRepository = crudRepositoryByClass.get(Types.rawTypeOf(type));
        if (crudRepository == null) throw new AssertionError(type + " is not an Entity!");
        return (CrudRepository<E, ?>) crudRepository;
    }

    @SuppressWarnings("unchecked")
    public <E> CrudRepository<E, ?> getRepositoryByType(Type type) {
        if (Types.isAssignableTo(type, CrudRepository.class)) {
            return (CrudRepository<E, ?>) applicationContext.getBeansOfType(Types.rawTypeOf(type)).values().stream()
                    .sorted((first, second) -> first.getClass() == type ? -1 : 1)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(type + " repository not found in application context!"));
        }
        throw new AssertionError(type + " is not a CrudRepository!");
    }

    private boolean hasEntityManagerSupport(Class<?> entityClass) {
        return entityManagerFactoryByClass.containsKey(entityClass) || sharedEntityManagerByClass.containsKey(entityClass);
    }
}
