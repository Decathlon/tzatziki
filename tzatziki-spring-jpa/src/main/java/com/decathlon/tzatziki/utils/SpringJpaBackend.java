package com.decathlon.tzatziki.utils;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import javax.sql.DataSource;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Spring flavour of the {@link JpaBackend}: entities are saved through their Spring Data repository and read through
 * Spring's shared EntityManager (joining the current transaction if any). Repositories of non-JPA entities
 * (e.g. Spring Data JDBC) are also supported.
 */
public class SpringJpaBackend extends JpaBackend {

    private final ApplicationContext applicationContext;
    private final Map<Class<?>, CrudRepository<?, ?>> crudRepositoryByClass;
    private final Map<EntityManagerFactory, EntityManager> sharedEntityManagers = new HashMap<>();

    public SpringJpaBackend(ApplicationContext applicationContext,
                            @Nullable List<LocalContainerEntityManagerFactoryBean> entityManagerFactories) {
        super(dataSourceByEntityManagerFactory(entityManagerFactories));
        this.applicationContext = applicationContext;
        this.crudRepositoryByClass = indexRepositoriesByEntityClass();
    }

    private static Map<EntityManagerFactory, DataSource> dataSourceByEntityManagerFactory(
            @Nullable List<LocalContainerEntityManagerFactoryBean> entityManagerFactories) {
        Map<EntityManagerFactory, DataSource> dataSources = new LinkedHashMap<>();
        if (entityManagerFactories != null) {
            entityManagerFactories.forEach(factory -> dataSources.putIfAbsent(factory.getObject(), factory.getDataSource()));
        }
        return dataSources;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<Class<?>, CrudRepository<?, ?>> indexRepositoriesByEntityClass() {
        return applicationContext.getBeansOfType(CrudRepository.class).values()
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
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, LinkedHashMap::new));
    }

    @Override
    public <E> void saveAll(Class<E> entityClass, List<E> entities) {
        this.<E>getRepositoryForEntity(entityClass).saveAll(entities);
    }

    @Override
    public <E> List<E> findAll(Class<E> entityClass, List<Map> expectedEntities) {
        if (isJpaEntity(entityClass)) {
            return super.findAll(entityClass, expectedEntities);
        }
        return StreamSupport.stream(this.<E>getRepositoryForEntity(entityClass).findAll().spliterator(), false).toList();
    }

    @Override
    public long count(Class<?> entityClass) {
        return isJpaEntity(entityClass) ? super.count(entityClass) : getRepositoryForEntity(entityClass).count();
    }

    @Override
    public Collection<Class<?>> getManagedEntityClasses() {
        Set<Class<?>> managedEntityClasses = new LinkedHashSet<>(super.getManagedEntityClasses());
        managedEntityClasses.addAll(crudRepositoryByClass.keySet());
        return managedEntityClasses;
    }

    @Override
    protected EntityManager entityManager(Class<?> entityClass) {
        // the shared EntityManager ignores close() and joins the current transaction if any
        return sharedEntityManagers.computeIfAbsent(entityManagerFactory(entityClass), SharedEntityManagerCreator::createSharedEntityManager);
    }

    @Override
    protected String qualifiedTableName(Class<?> entityClass) {
        String jpaTableName = super.qualifiedTableName(entityClass);
        Table table = entityClass.getAnnotation(Table.class);
        if (jpaTableName != null || table == null) return jpaTableName;
        String name = table.name().isBlank() ? table.value() : table.name();
        if (name.isBlank()) return null;
        return table.schema().isBlank() ? name : table.schema() + "." + name;
    }

    private boolean isJpaEntity(Class<?> entityClass) {
        return super.getManagedEntityClasses().contains(entityClass);
    }

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
}
