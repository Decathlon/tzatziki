package com.decathlon.tzatziki.utils;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.repository.CrudRepository;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Spring-based JPA backend that delegates to CrudRepository and Spring EntityManagerFactory.
 * This is the glue between the pure JPA Cucumber steps and Spring Data infrastructure.
 */
@Slf4j
public class SpringJpaBackend implements JpaBackend {

    private final ApplicationContext applicationContext;
    private final List<LocalContainerEntityManagerFactoryBean> entityManagerFactories;
    private final List<EntityManager> entityManagers;
    private Map<Type, CrudRepository<?, ?>> crudRepositoryByClass;
    private Map<Type, EntityManager> entityManagerByClass;
    private Map<String, Type> entityClassByTableName;

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
                    .sorted((e1, e2) -> {
                        if (e1.getValue() instanceof Class) return -1;
                        return e2.getValue() instanceof Class ? 1 : 0;
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
                            (v1, v2) -> v1
                    ));
        }

        if (entityManagerByClass == null && entityManagers != null) {
            entityManagerByClass = entityManagers.stream()
                    .flatMap(em -> em.getMetamodel().getEntities().stream()
                            .map(entityType -> Map.entry((Type) entityType.getJavaType(), em)))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (em1, em2) -> em1
                    ));
        }
        if (entityManagerByClass == null) {
            entityManagerByClass = new HashMap<>();
        }

        if (entityClassByTableName == null) {
            entityClassByTableName = crudRepositoryByClass.keySet().stream()
                    .map(type -> (Class<?>) type)
                    .sorted((c1, c2) -> {
                        if (TypeParser.getDefaultPackage() == null) return 0;
                        if (c1.getPackageName().startsWith(TypeParser.getDefaultPackage())) return -1;
                        return c2.getPackageName().startsWith(TypeParser.getDefaultPackage()) ? 1 : 0;
                    })
                    .<Map.Entry<String, Class<?>>>mapMulti((clazz, consumer) -> {
                        String tableName = getTableName(clazz);
                        if (tableName != null) consumer.accept(Map.entry(tableName, clazz));
                    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (t1, t2) -> t1));
        }
    }

    @Override
    public EntityManager getEntityManager(Class<?> entityClass) {
        return entityManagerByClass.get(entityClass);
    }

    @Override
    public DataSource getDataSource(Class<?> entityClass) {
        if (entityManagerFactories == null || entityManagerFactories.isEmpty()) return null;
        return entityManagerFactories.stream()
                .filter(emf -> emf.getPersistenceUnitInfo() != null)
                .filter(emf -> emf.getPersistenceUnitInfo().getManagedClassNames().contains(entityClass.getName()))
                .map(LocalContainerEntityManagerFactoryBean::getDataSource)
                .findFirst()
                .orElse(entityManagerFactories.get(0).getDataSource());
    }

    @Override
    public List<DataSource> getAllDataSources() {
        if (entityManagerFactories == null) return List.of();
        return entityManagerFactories.stream()
                .map(LocalContainerEntityManagerFactoryBean::getDataSource)
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> void saveAll(Class<E> entityClass, List<E> entities) {
        CrudRepository<E, ?> repository = (CrudRepository<E, ?>) crudRepositoryByClass.get(entityClass);
        if (repository == null) throw new AssertionError(entityClass + " is not an Entity!");
        repository.saveAll(entities);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> List<E> findAll(Class<E> entityClass) {
        CrudRepository<E, ?> repository = (CrudRepository<E, ?>) crudRepositoryByClass.get(entityClass);
        if (repository == null) throw new AssertionError(entityClass + " is not an Entity!");
        return StreamSupport.stream(repository.findAll().spliterator(), false).toList();
    }

    @Override
    public <E> long count(Class<E> entityClass) {
        CrudRepository<?, ?> repository = crudRepositoryByClass.get(entityClass);
        if (repository == null) throw new AssertionError(entityClass + " is not an Entity!");
        return repository.count();
    }

    @Override
    public <E> void truncate(Class<E> entityClass) {
        String tableWithSchema = getTableNameWithSchema(entityClass);
        if (tableWithSchema != null) {
            DataSource dataSource = getDataSource(entityClass);
            if (dataSource != null) {
                DatabaseCleaner.truncateTable(dataSource, tableWithSchema);
            }
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

    // --- Spring-specific helpers ---

    @SuppressWarnings("unchecked")
    public <E> CrudRepository<E, ?> getRepositoryForEntity(Type type) {
        CrudRepository<?, ?> crudRepository = crudRepositoryByClass.get(type);
        if (crudRepository == null) throw new AssertionError(type + " is not an Entity!");
        return (CrudRepository<E, ?>) crudRepository;
    }

    public <E> CrudRepository<E, ?> getRepositoryByType(Type type) {
        if (Types.isAssignableTo(type, CrudRepository.class)) {
            return ((CrudRepository<E, ?>) applicationContext.getBeansOfType(Types.rawTypeOf(type)).values().stream()
                    .sorted((b1, b2) -> b1.getClass() == type ? -1 : 1)
                    .findFirst().get());
        }
        throw new AssertionError(type + " is not a CrudRepository!");
    }

    // --- Table name resolution (supports both JPA and Spring Data annotations) ---

    private Pair<String, String> getTableSchemaAndName(Class<?> clazz) {
        Annotation tableAnnotation = (Annotation) Optional.ofNullable(clazz.getAnnotation(PersistenceUtil.getPersistenceClass("Table")))
                .orElseGet(() -> clazz.getAnnotation(org.springframework.data.relational.core.mapping.Table.class));
        return Optional.ofNullable(tableAnnotation)
                .map(annotation -> {
                    String tableName = (String) AnnotationUtils.getValue(annotation, "name");
                    if (StringUtils.isBlank(tableName)) {
                        tableName = (String) AnnotationUtils.getValue(annotation, "value");
                    }
                    String schemaName = (String) AnnotationUtils.getValue(annotation, "schema");
                    return Pair.of(schemaName, tableName);
                }).orElse(Pair.of(null, null));
    }

    @Nullable
    private String getTableName(Class<?> clazz) {
        return getTableSchemaAndName(clazz).getValue();
    }

    @Nullable
    private String getTableNameWithSchema(Class<?> clazz) {
        Pair<String, String> tableSchemaAndName = getTableSchemaAndName(clazz);
        return StringUtils.isNotBlank(tableSchemaAndName.getKey())
                ? tableSchemaAndName.getKey() + "." + tableSchemaAndName.getValue()
                : tableSchemaAndName.getValue();
    }
}
