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
    private Map<Class<?>, CrudRepository<?, ?>> crudRepositoryByClass;
    private Map<Class<?>, EntityManagerFactory> entityManagerFactoryByClass;
    private Map<Class<?>, DataSource> dataSourceByClass;

    public SpringJpaBackend(ApplicationContext applicationContext,
                            @Nullable List<LocalContainerEntityManagerFactoryBean> entityManagerFactories) {
        this.applicationContext = applicationContext;
        this.entityManagerFactories = entityManagerFactories;
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
        throw new AssertionError(entityClass + " is not an Entity!");
    }

    @Override
    public <E> void withTransaction(Class<E> entityClass, Consumer<EntityManager> callback) {
        EntityManagerFactory entityManagerFactory = entityManagerFactoryByClass.get(entityClass);
        if (entityManagerFactory == null) {
            throw new AssertionError(entityClass + " is not an Entity!");
        }
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
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> void saveAll(Class<E> entityClass, List<E> entities) {
        // Prefer the Spring Data repository so that inserts go through Spring's transaction management
        // (JTA, custom repository logic...) like the pre-split tzatziki-spring-jpa did.
        CrudRepository<E, ?> repository = (CrudRepository<E, ?>) crudRepositoryByClass.get(entityClass);
        if (repository != null) {
            repository.saveAll(entities);
            return;
        }
        if (hasEntityManagerSupport(entityClass)) {
            JpaBackend.super.saveAll(entityClass, entities);
            return;
        }
        throw new AssertionError(entityClass + " is not an Entity!");
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
            Class<?> repositoryType = Types.rawTypeOf(type);
            // beans are proxies: prefer the one directly implementing the requested interface over its sub-interfaces
            return (CrudRepository<E, ?>) applicationContext.getBeansOfType(repositoryType).values().stream()
                    .min(Comparator.comparing(bean -> !Arrays.asList(bean.getClass().getInterfaces()).contains(repositoryType)))
                    .orElseThrow(() -> new AssertionError(type + " repository not found in application context!"));
        }
        throw new AssertionError(type + " is not a CrudRepository!");
    }

    private boolean hasEntityManagerSupport(Class<?> entityClass) {
        return entityManagerFactoryByClass.containsKey(entityClass);
    }
}
