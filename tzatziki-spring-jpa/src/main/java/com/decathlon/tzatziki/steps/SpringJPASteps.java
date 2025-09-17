package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.jpa.SpecHints;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.InsertionMode.INSERTION_MODE;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class SpringJPASteps {

    public static final String ORDER_PATTERN = "[a-zA-Z]+(?: asc| desc)?";
    public static final String ORDER_SEPARATOR = " and ";

    static {
        DynamicTransformers.register(Type.class, TypeParser::parse);
        DynamicTransformers.register(InsertionMode.class, InsertionMode::parse);
    }

    public static boolean autoclean = true;
    public static List<String> schemasToClean = List.of("public");

    private final List<LocalContainerEntityManagerFactoryBean> entityManagerFactories;
    private final List<EntityManager> entityManagers;
    private Map<Type, CrudRepository<?, ?>> crudRepositoryByClass;
    private Map<Type, EntityManager> entityManagerByClass;
    private Map<String, Type> entityClassByTableName;
    private boolean disableTriggers = true;
    private final ObjectSteps objects;
    private final SpringSteps spring;

    static {
        JacksonMapper.with(objectMapper -> objectMapper.registerModule(PersistenceUtil.getMapperModule()));
    }

    public SpringJPASteps(ObjectSteps objects, SpringSteps spring, @Nullable List<LocalContainerEntityManagerFactoryBean> entityManagerFactories, List<EntityManager> entityManagers) {
        this.objects = objects;
        this.spring = spring;
        this.entityManagerFactories = entityManagerFactories;
        this.entityManagers = entityManagers;
    }

    @Before
    public void before() {
        if (autoclean) {
            dataSources().forEach(dataSource -> {
                DatabaseCleaner.clean(dataSource, schemasToClean);
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.enable);
            });
        }

        if (crudRepositoryByClass == null) {
            crudRepositoryByClass = spring.applicationContext().getBeansOfType(CrudRepository.class).values()
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

        if (entityManagerByClass == null) {
            entityManagerByClass = entityManagers.stream()
                    .flatMap(em -> em.getMetamodel().getEntities().stream()
                            .map(entityType -> Map.entry((Type) entityType.getJavaType(), em)))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (em1, em2) -> em1 // Keep first entity manager if duplicate
                    ));
        }

        if (entityClassByTableName == null) {
            entityClassByTableName = crudRepositoryByClass.keySet().stream()
                    .map(type -> (Class<?>) type)
                    .sorted((c1, c2) -> {
                        if (TypeParser.defaultPackage == null) return 0;

                        if (c1.getPackageName().startsWith(TypeParser.defaultPackage)) return -1;

                        return c2.getPackageName().startsWith(TypeParser.defaultPackage) ? 1 : 0;
                    })
                    .<Map.Entry<String, Class<?>>>mapMulti((clazz, consumer) -> {
                        String tableName = getTableName(clazz);
                        if (tableName != null) consumer.accept(Map.entry(tableName, clazz));
                    }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (t1, t2) -> t1));
        }
    }


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
        return StringUtils.isNotBlank(tableSchemaAndName.getKey()) ? tableSchemaAndName.getKey() + "." + tableSchemaAndName.getValue() : tableSchemaAndName.getValue();
    }

    @NotNull
    private Stream<DataSource> dataSources() {
        if (entityManagerFactories != null) {
            return entityManagerFactories.stream().map(LocalContainerEntityManagerFactoryBean::getDataSource);
        }
        return Stream.of();
    }

    @Given(THAT + GUARD + "the " + TYPE + " repository will contain" + INSERTION_MODE + ":$")
    public void the_repository_will_contain(Guard guard, Type repositoryType, InsertionMode insertionMode, Object content) {
        the_repository_will_contain(guard, getRepositoryByType(repositoryType), insertionMode, objects.resolve(content));
    }

    @Given(THAT + GUARD + "the ([^ ]+) table will contain" + INSERTION_MODE + ":$")
    public void the_table_will_contain(Guard guard, String table, InsertionMode insertionMode, Object content) {
        the_repository_will_contain_with_type(guard, getRepositoryForTable(table), insertionMode, entityTypeByTableNameOrClassName(table), objects.resolve(content));
    }

    @Given(THAT + GUARD + "the " + TYPE + " entities will contain" + INSERTION_MODE + ":$")
    public void the_entities_will_contain(Guard guard, Type type, InsertionMode insertionMode, Object content) {
        the_repository_will_contain_with_type(guard, getRepositoryForEntity(type), insertionMode, type, objects.resolve(content));
    }

    @Given(THAT + GUARD + "the triggers are (enable|disable)d$")
    public void enable_triggers(Guard guard, String action) {
        guard.in(objects, () -> disableTriggers = action.equals("disable"));
    }

    @Then(THAT + GUARD + "the " + TYPE + " repository (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_repository_contains(Guard guard, Type type, Comparison comparison, Object content) {
        the_repository_contains(guard, getRepositoryByType(type), comparison, objects.resolve(content));
    }

    @Then(THAT + GUARD + "the ([^ ]+) table (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_table_contains(Guard guard, String table, Comparison comparison, Object content) {
        the_repository_contains(guard, getRepositoryForTable(table), comparison, objects.resolve(content));
    }

    @Then(THAT + GUARD + "the " + TYPE + " entities (?:still )?contain" + COMPARING_WITH + ":$")
    public void the_entities_contain(Guard guard, Type type, Comparison comparison, Object content) {
        the_repository_contains(guard, getRepositoryForEntity(type), comparison, objects.resolve(content));
    }

    @Then(THAT + GUARD + "the " + TYPE + " repository (?:still )?contains nothing$")
    public void the_repository_contains_nothing(Guard guard, Type type) {
        the_repository_contains_nothing(guard, getRepositoryByType(type));
    }

    @Then(THAT + GUARD + "the ([^ ]+) table (?:still )?contains nothing$")
    public void the_table_contains_nothing(Guard guard, String table) {
        the_repository_contains_nothing(guard, getRepositoryForTable(table));
    }

    @Then(THAT + GUARD + "the " + TYPE + " entities (?:still )?contain nothing$")
    public void the_entities_contain_nothing(Guard guard, Type type) {
        the_repository_contains_nothing(guard, getRepositoryForEntity(type));
    }

    @Then(THAT + GUARD + VARIABLE + " is the ([^ ]+) table content$")
    public void add_table_content_to_variable(Guard guard, String name, String table) {
        add_repository_content_to_variable(guard, name, getRepositoryForTable(table));
    }

    @Then(THAT + GUARD + VARIABLE + " is the " + TYPE + " entities$")
    public void add_entities_to_variable(Guard guard, String name, Type type) {
        add_repository_content_to_variable(guard, name, getRepositoryForEntity(type));
    }


    @Then(THAT + GUARD + VARIABLE + " is the ([^ ]+) table content ordered by (" + ORDER_PATTERN + "(?:" + ORDER_SEPARATOR + ORDER_PATTERN + ")*)$")
    public void add_ordered_table_content_to_variable(Guard guard, String name, String table, String orders) {
        add_ordered_repository_content_to_variable(guard, name, getRepositoryForTable(table), parseSort(orders));
    }

    @Then(THAT + GUARD + VARIABLE + " is the " + TYPE + " entities ordered by (" + ORDER_PATTERN + "(?:" + ORDER_SEPARATOR + ORDER_PATTERN + ")*)$")
    public void add_ordered_entities_to_variable(Guard guard, String name, Type type, String orders) {
        add_ordered_repository_content_to_variable(guard, name, getRepositoryForEntity(type), parseSort(orders));
    }

    @NotNull
    private Sort parseSort(String orders) {
        return Sort.by(Arrays.stream(orders.split(ORDER_SEPARATOR)).map(this::parseOrder).toList());
    }

    private Sort.Order parseOrder(String propertyAndDirection) {
        String[] elmts = propertyAndDirection.split(" ");
        return elmts.length > 1 ? new Sort.Order(Sort.Direction.fromString(elmts[1]), elmts[0]) : Sort.Order.by(elmts[0]);
    }

    private void the_repository_contains_nothing(Guard guard, CrudRepository<Object, ?> repositoryOfEntity) {
        guard.in(objects, () -> assertThat(repositoryOfEntity.count()).isZero());
    }

    public <E> void the_repository_will_contain(Guard guard, CrudRepository<E, ?> repository, InsertionMode insertionMode, String entities) {
        the_repository_will_contain_with_type(guard, repository, insertionMode, getEntityType(repository), entities);
    }

    public <E> void the_repository_will_contain_with_type(Guard guard, CrudRepository<E, ?> repository, InsertionMode insertionMode, Type entityType, String entities) {
        guard.in(objects, () -> {
            if (!(entityType instanceof Class<?>)) return;

            Class<E> entityClass = (Class<E>) entityType;
            if (disableTriggers) {
                dataSources().forEach(dataSource -> DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.disable));
            }
            if (insertionMode == InsertionMode.ONLY) {
                String tableWithSchema = getTableNameWithSchema(entityClass);
                DataSource dataSource = entityManagerFactories.stream()
                        .filter(entityManagerFactory -> entityManagerFactory.getPersistenceUnitInfo() != null)
                        .filter(entityManagerFactory -> entityManagerFactory.getPersistenceUnitInfo().getManagedClassNames().contains(entityClass.getName()))
                        .map(LocalContainerEntityManagerFactoryBean::getDataSource).findFirst()
                        .orElse(entityManagerFactories.get(0).getDataSource());
                new JdbcTemplate(dataSource).update("TRUNCATE %s RESTART IDENTITY CASCADE".formatted(tableWithSchema));
            }
            repository.saveAll(Mapper.readAsAListOf(entities, entityClass));
            if (disableTriggers) {
                dataSources().forEach(dataSource -> DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.enable));
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <E> void the_repository_contains(Guard guard, CrudRepository<E, ?> repository, Comparison comparison, String entities) {
        guard.in(objects, () -> {
            Class<E> entityClass = getEntityType(repository);
            List<Map> expectedEntities = Mapper.readAsAListOf(entities, Map.class);
            List<E> actualEntities = findAllEntities(repository, entityClass, expectedEntities);
            comparison.compare(actualEntities, expectedEntities);
        });
    }

    public <E> void add_repository_content_to_variable(Guard guard, String name, CrudRepository<E, ?> repository) {
        guard.in(objects, () -> objects.add(name, StreamSupport.stream(repository.findAll().spliterator(), false).toList()));
    }

    @SuppressWarnings("unchecked")
    public <E> void add_ordered_repository_content_to_variable(Guard guard, String name, CrudRepository<E, ?> repository, Sort sort) {
        if (repository instanceof PagingAndSortingRepository sortingRepository) {
            guard.in(objects, () -> objects.add(name, StreamSupport.stream(sortingRepository.findAll(sort).spliterator(), false).toList()));
        } else {
            throw new AssertionError(repository.getClass() + " is not a PagingAndSortingRepository!");
        }
    }

    public <E> CrudRepository<E, ?> getRepositoryForTable(String table) {
        return getRepositoryForEntity(Optional.ofNullable(this.entityClassByTableName.get(table)).orElseGet(() -> TypeParser.parse(table)));
    }

    @Nullable
    private Type entityTypeByTableNameOrClassName(String entityTableOrClass) {
        return Optional.ofNullable(entityClassByTableName.get(entityTableOrClass)).orElseGet(() -> TypeParser.parse(entityTableOrClass));
    }

    @SuppressWarnings({"unchecked"})
    public <E> CrudRepository<E, ?> getRepositoryForEntity(Type type) {
        CrudRepository<?, ?> crudRepository = crudRepositoryByClass.get(type);
        if (crudRepository == null) throw new AssertionError(type + " is not an Entity!");

        return (CrudRepository<E, ?>) crudRepository;
    }

    public <E> CrudRepository<E, ?> getRepositoryByType(Type type) {
        if (Types.isAssignableTo(type, CrudRepository.class)) {
            return ((CrudRepository<E, ?>) spring.applicationContext().getBeansOfType(Types.rawTypeOf(type)).values().stream()
                    .sorted((b1, b2) -> b1.getClass() == type ? -1 : 1)
                    .findFirst().get());
        }
        throw new AssertionError(type + " is not a CrudRepository!");
    }

    public <E> Class<E> getEntityType(CrudRepository<E, ?> repository) {
        return Types.rawTypeArgumentOf(repository.getClass().getInterfaces()[0].getGenericInterfaces()[0]);
    }

    /**
     * Fetches all entities from the repository, using EntityManager for loading only the expected fields if possible
     */
    private <E> List<E> findAllEntities(CrudRepository<E, ?> repository, Class<E> entityClass, List<Map> expectedEntities) {
        EntityManager entityManager = entityManagerByClass.get(entityClass);
        if (entityManager == null) {
            // Fallback to simple findAll if no EntityManager is found for the entity class (A JDBC repository for example)
            return StreamSupport.stream(repository.findAll().spliterator(), false).toList();
        }
        return findAllEntitiesWithOnlyExpectedFields(entityManager, entityClass, expectedEntities);
    }

    /**
     * Fetches all entities of the given type using EntityManager with only the fields present in expectedEntities
     * to avoid loading large object when not needed.
     */
    private <E> List<E> findAllEntitiesWithOnlyExpectedFields(EntityManager entityManager, Class<E> entityClass, List<Map> expectedEntities) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<E> query = cb.createQuery(entityClass);
        Root<E> root = query.from(entityClass);

        // Select all entities
        query.select(root);

        TypedQuery<E> typedQuery = entityManager.createQuery(query);

        // Use fetch graph hint to load expected fields only
        try {
            EntityGraph<E> entityGraph = EntityGraphUtils.createEntityGraph(entityManager, entityClass, expectedEntities);
            typedQuery.setHint(SpecHints.HINT_SPEC_FETCH_GRAPH, entityGraph);
        } catch (Exception e) {
            // If entity graph creation fails, fallback to default loading
            log.debug("Could not get entity graph for {}, loading all fields", entityClass, e);
        }

        return typedQuery.getResultList();
    }
}
