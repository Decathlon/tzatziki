# User Provided Header
Tzatziki DB / JPA / Spring JPA module reference.
- DatabaseSteps.java (tzatziki-db), JpaSteps.java (tzatziki-jpa), and SpringJPASteps.java (tzatziki-spring-jpa) define @Given/@When/@Then patterns for table-level SQL fixtures, JPA entity management, and Spring Data repository assertions.
- .feature files demonstrate valid DB/JPA step usage with YAML doc strings and tables for database state.
- Prefer YAML (`"""yml`) for entity fixtures and expected data.


# Directory Structure
```
tzatziki-db/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                DatabaseSteps.java
tzatziki-jpa/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                JpaSteps.java
tzatziki-spring-jpa/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                SpringJPASteps.java
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                spring-jpa.feature
```

# Files

## File: tzatziki-db/src/main/java/com/decathlon/tzatziki/steps/DatabaseSteps.java
```java
package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.*;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.InsertionMode.INSERTION_MODE;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure database Cucumber step definitions for table-level operations.
 * <p>
 * Uses {@link DbBackend} to perform INSERT, SELECT, COUNT, and TRUNCATE.
 * By default, a {@link JdbcBackend} is used with the first registered DataSource.
 * Upper layers (tzatziki-jpa, tzatziki-spring-jpa) can override the backend via
 * {@link #registerBackend(DbBackend)} to use JPA entity resolution and entity graphs.
 */
@Slf4j
@SuppressWarnings("java:S100")
public class DatabaseSteps {

    public static final String TABLE_PATTERN = "([^ ]+)";

    public static boolean autoclean = true;
    public static List<String> schemasToClean = List.of("public");

    private static final List<DataSource> registeredDataSources = new ArrayList<>();
    private static DbBackend backend;
    private boolean disableTriggers = true;

    private final ObjectSteps objects;

    static {
        DynamicTransformers.register(InsertionMode.class, InsertionMode::parse);
    }

    public DatabaseSteps(ObjectSteps objects) {
        this.objects = objects;
    }

    /**
     * Register a DbBackend implementation.
     * Called by upper layers (e.g., JpaSteps) to override the default JDBC backend.
     */
    public static void registerBackend(DbBackend dbBackend) {
        backend = dbBackend;
    }

    /**
     * Get the currently registered backend. Returns null if none registered.
     */
    public static DbBackend getBackend() {
        return backend;
    }

    /**
     * Register a DataSource to be managed by DatabaseSteps (cleaned on @Before, triggers managed).
     */
    public static void registerDataSource(DataSource dataSource) {
        if (!registeredDataSources.contains(dataSource)) {
            registeredDataSources.add(dataSource);
        }
    }

    /**
     * Clear all registered data sources (for test isolation).
     */
    public static void clearDataSources() {
        registeredDataSources.clear();
        backend = null;
    }

    public static List<DataSource> getRegisteredDataSources() {
        return List.copyOf(registeredDataSources);
    }

    @Before(order = 100)
    public void before() {
        initializeDefaultBackend();
        if (autoclean) {
            registeredDataSources.forEach(dataSource -> {
                DatabaseCleaner.clean(dataSource, schemasToClean);
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.enable);
            });
        }
    }

    private static void initializeDefaultBackend() {
        if (backend == null && !registeredDataSources.isEmpty()) {
            backend = new JdbcBackend(registeredDataSources.get(0));
        }
    }

    private static DbBackend requireBackend() {
        initializeDefaultBackend();
        if (backend == null) {
            throw new IllegalStateException("No DbBackend or DataSource registered. Call DatabaseSteps.registerDataSource(...) or DatabaseSteps.registerBackend(...) first.");
        }
        return backend;
    }

    // ---- Table-level step definitions ----

    @Given(THAT + GUARD + "the " + TABLE_PATTERN + " table will contain" + INSERTION_MODE + ":$")
    public void the_table_will_contain(Guard guard, String table, InsertionMode insertionMode, Object content) {
        guard.in(objects, () -> {
            DbBackend activeBackend = requireBackend();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List) Mapper.readAsAListOf(objects.resolve(content), Map.class);
            withTriggersDisabledIfNeeded(() -> {
                if (insertionMode == InsertionMode.ONLY) {
                    activeBackend.truncate(table);
                }
                activeBackend.insertRows(table, rows);
            });
        });
    }

    @Then(THAT + GUARD + "the " + TABLE_PATTERN + " table (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_table_contains(Guard guard, String table, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> expectedRows = (List) Mapper.readAsAListOf(objects.resolve(content), Map.class);
            List<Map<String, Object>> actualRows = requireBackend().queryAll(table, expectedRows);
            comparison.compare(actualRows, expectedRows);
        });
    }

    @Then(THAT + GUARD + "the " + TABLE_PATTERN + " table (?:still )?contains nothing$")
    public void the_table_contains_nothing(Guard guard, String table) {
        guard.in(objects, () -> assertThat(requireBackend().count(table)).isZero());
    }

    @Then(THAT + GUARD + VARIABLE + " is the " + TABLE_PATTERN + " table content$")
    public void add_table_content_to_variable(Guard guard, String name, String table) {
        guard.in(objects, () -> objects.add(name, requireBackend().queryAll(table, null)));
    }

    // ---- Trigger management ----

    @Given(THAT + GUARD + "the triggers are (enable|disable)d$")
    public void enable_triggers(Guard guard, String action) {
        guard.in(objects, () -> disableTriggers = action.equals("disable"));
    }

    public boolean isDisableTriggers() {
        return disableTriggers;
    }

    /**
     * Runs the given insertion with the triggers disabled (unless "the triggers are enabled" was set),
     * re-enabling them afterwards even if the insertion fails.
     */
    public void withTriggersDisabledIfNeeded(Runnable insertion) {
        boolean toggleTriggers = disableTriggers;
        if (toggleTriggers) {
            disableTriggersOnAllDataSources();
        }
        try {
            insertion.run();
        } finally {
            if (toggleTriggers) {
                enableTriggersOnAllDataSources();
            }
        }
    }

    public void disableTriggersOnAllDataSources() {
        registeredDataSources.forEach(dataSource ->
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.disable));
    }

    public void enableTriggersOnAllDataSources() {
        registeredDataSources.forEach(dataSource ->
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.enable));
    }
}
```

## File: tzatziki-jpa/src/main/java/com/decathlon/tzatziki/steps/JpaSteps.java
```java
package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.InsertionMode.INSERTION_MODE;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure JPA Cucumber step definitions for entity-typed CRUD operations.
 * <p>
 * Uses JpaBackend interface to abstract the persistence layer.
 * Table-level steps (by table name) are in {@link DatabaseSteps} and delegate
 * to the JPA backend directly since {@link JpaBackend} extends {@link DbBackend}.
 * <p>
 * No Spring dependency — works with any JPA implementation.
 */
@SuppressWarnings("java:S100")
public class JpaSteps {

    private static JpaBackend backend;

    static {
        DynamicTransformers.register(Type.class, TypeParser::parse);
        if (Mapper.isJackson3()) {
            Jackson3Mapper.with(objectMapper -> objectMapper.rebuild().addModule(Jackson3PersistenceUtil.getMapperModule()).build());
        } else {
            JacksonMapper.with(objectMapper -> objectMapper.registerModule(PersistenceUtil.getMapperModule()));
        }
    }

    private final ObjectSteps objects;
    private final DatabaseSteps databaseSteps;

    public JpaSteps(ObjectSteps objects, DatabaseSteps databaseSteps) {
        this.objects = objects;
        this.databaseSteps = databaseSteps;
    }

    /**
     * Register a JpaBackend implementation.
     * Called by upper layers (e.g., SpringJpaBackend @Before hook) or directly in standalone JPA tests.
     * Since JpaBackend extends DbBackend, this also registers the backend with DatabaseSteps
     * for table-level operations — no adapter needed.
     */
    public static void registerBackend(JpaBackend jpaBackend) {
        backend = jpaBackend;
        // Register all datasources with DatabaseSteps for autoclean
        jpaBackend.getAllDataSources().forEach(DatabaseSteps::registerDataSource);
        // JpaBackend extends DbBackend, so it can be registered directly
        DatabaseSteps.registerBackend(jpaBackend);
    }

    public static JpaBackend getBackend() {
        return backend;
    }

    private static JpaBackend requireBackend() {
        if (backend == null) {
            throw new IllegalStateException("No JpaBackend registered. Call JpaSteps.registerBackend(...) first.");
        }
        return backend;
    }

    // ---- Entity-typed step definitions (require JPA class resolution) ----

    @Given(THAT + GUARD + "the " + TYPE + " entities will contain" + INSERTION_MODE + ":$")
    public void the_entities_will_contain(Guard guard, Type type, InsertionMode insertionMode, Object content) {
        guard.in(objects, () -> insertEntitiesByType(type, insertionMode, objects.resolve(content)));
    }

    @Then(THAT + GUARD + "the " + TYPE + " entities (?:still )?contain" + COMPARING_WITH + ":$")
    public void the_entities_contain(Guard guard, Type type, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            Class<?> entityClass = (Class<?>) type;
            List<Map> expectedEntities = Mapper.readAsAListOf(objects.resolve(content), Map.class);
            List<?> actualEntities = findAllEntitiesWithOnlyExpectedFields(entityClass, expectedEntities);
            comparison.compare(actualEntities, expectedEntities);
        });
    }

    @Then(THAT + GUARD + "the " + TYPE + " entities (?:still )?contain nothing$")
    public void the_entities_contain_nothing(Guard guard, Type type) {
        guard.in(objects, () -> assertThat(requireBackend().count((Class<?>) type)).isZero());
    }

    @Then(THAT + GUARD + VARIABLE + " is the " + TYPE + " entities$")
    public void add_entities_to_variable(Guard guard, String name, Type type) {
        guard.in(objects, () -> objects.add(name, requireBackend().findAll((Class<?>) type)));
    }

    // ---- internal methods ----

    @SuppressWarnings("unchecked")
    private <E> void insertEntitiesByType(Type type, InsertionMode insertionMode, String entities) {
        if (!(type instanceof Class<?>)) return;
        Class<E> entityClass = (Class<E>) type;
        doInsert(entityClass, insertionMode, entities);
    }

    private <E> void doInsert(Class<E> entityClass, InsertionMode insertionMode, String entities) {
        JpaBackend activeBackend = requireBackend();
        List<E> parsedEntities = Mapper.readAsAListOf(entities, entityClass);
        databaseSteps.withTriggersDisabledIfNeeded(() -> {
            if (insertionMode == InsertionMode.ONLY) {
                activeBackend.truncate(entityClass);
            }
            activeBackend.saveAll(entityClass, parsedEntities);
        });
    }

    @SuppressWarnings("unchecked")
    private <E> List<E> findAllEntitiesWithOnlyExpectedFields(Class<?> entityClass, List<Map> expectedEntities) {
        Class<E> clazz = (Class<E>) entityClass;
        return requireBackend().findAllWithExpectedFields(clazz, expectedEntities);
    }
}
```

## File: tzatziki-spring-jpa/src/main/java/com/decathlon/tzatziki/steps/SpringJPASteps.java
```java
package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.InsertionMode.INSERTION_MODE;
import static com.decathlon.tzatziki.utils.Patterns.*;

/**
 * Spring-specific JPA Cucumber steps.
 * <p>
 * Delegates common entity operations to {@link JpaSteps} via {@link SpringJpaBackend}.
 * Retains only Spring-specific functionality:
 * - CrudRepository-typed insertion/query steps
 * - PagingAndSortingRepository ordered queries
 * - Spring context initialization and backend registration
 */
@Slf4j
@SuppressWarnings({"java:S100", "java:S1133"}) // S1133: deprecated delegates are kept on purpose for backward compatibility
public class SpringJPASteps {

    public static final String ORDER_PATTERN = "[a-zA-Z]+(?: asc| desc)?";
    public static final String ORDER_SEPARATOR = " and ";

    /**
     * Backward-compatible field: whether the database is cleaned before each scenario.
     * Propagated to {@link DatabaseSteps#autoclean} before each scenario.
     */
    public static boolean autoclean = true;

    /**
     * Backward-compatible field: schemas to clean before each scenario.
     * Propagated to {@link DatabaseSteps#schemasToClean} before each scenario.
     */
    public static List<String> schemasToClean = List.of("public");

    private final ObjectSteps objects;
    private final SpringSteps spring;
    private final DatabaseSteps databaseSteps;
    private final JpaSteps jpaSteps;
    private final List<LocalContainerEntityManagerFactoryBean> entityManagerFactories;
    private SpringJpaBackend springBackend;

    public SpringJPASteps(ObjectSteps objects, SpringSteps spring, DatabaseSteps databaseSteps, JpaSteps jpaSteps,
                          @Nullable List<LocalContainerEntityManagerFactoryBean> entityManagerFactories) {
        this.objects = objects;
        this.spring = spring;
        this.databaseSteps = databaseSteps;
        this.jpaSteps = jpaSteps;
        this.entityManagerFactories = entityManagerFactories;
    }

    @Before(order = 10)
    public void before() {
        // Register Spring's @Transient annotation for PersistenceUtil serialization filtering
        PersistenceUtil.registerTransientAnnotation(org.springframework.data.annotation.Transient.class);
        DatabaseSteps.autoclean = autoclean;
        DatabaseSteps.schemasToClean = schemasToClean;
        if (springBackend == null) {
            springBackend = new SpringJpaBackend(spring.applicationContext(), entityManagerFactories);
        }
        springBackend.initialize();
        JpaSteps.registerBackend(springBackend);
    }

    // --- Spring CrudRepository-typed steps (not available in pure JPA) ---

    @Given(THAT + GUARD + "the " + TYPE + " repository will contain" + INSERTION_MODE + ":$")
    public void the_repository_will_contain(Guard guard, Type repositoryType, InsertionMode insertionMode, Object content) {
        guard.in(objects, () -> {
            CrudRepository<Object, ?> repository = springBackend.getRepositoryByType(repositoryType);
            insertIntoRepository(repository, insertionMode, getEntityType(repository), objects.resolve(content));
        });
    }

    private <E> void insertIntoRepository(CrudRepository<E, ?> repository, InsertionMode insertionMode, Type entityType, String content) {
        if (!(entityType instanceof Class<?>)) return;
        @SuppressWarnings("unchecked")
        Class<E> entityClass = (Class<E>) entityType;
        List<E> entities = Mapper.readAsAListOf(content, entityClass);
        databaseSteps.withTriggersDisabledIfNeeded(() -> {
            if (insertionMode == InsertionMode.ONLY) {
                springBackend.truncate(entityClass);
            }
            repository.saveAll(entities);
        });
    }

    @Then(THAT + GUARD + "the " + TYPE + " repository (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_repository_contains(Guard guard, Type type, Comparison comparison, Object content) {
        assertRepositoryContains(guard, springBackend.getRepositoryByType(type), comparison, objects.resolve(content));
    }

    @Then(THAT + GUARD + "the " + TYPE + " repository (?:still )?contains nothing$")
    public void the_repository_contains_nothing(Guard guard, Type type) {
        guard.in(objects, () -> {
            CrudRepository<Object, ?> repository = springBackend.getRepositoryByType(type);
            org.assertj.core.api.Assertions.assertThat(repository.count()).isZero();
        });
    }

    // --- PagingAndSortingRepository ordered queries (Spring-only) ---

    @Then(THAT + GUARD + VARIABLE + " is the ([^ ]+) table content ordered by (" + ORDER_PATTERN + "(?:" + ORDER_SEPARATOR + ORDER_PATTERN + ")*)$")
    public void add_ordered_table_content_to_variable(Guard guard, String name, String table, String orders) {
        guard.in(objects, () -> {
            CrudRepository<Object, ?> repository = springBackend.getRepositoryForEntity(springBackend.resolveEntityType(table));
            addOrderedContent(name, repository, parseSort(orders));
        });
    }

    @Then(THAT + GUARD + VARIABLE + " is the " + TYPE + " entities ordered by (" + ORDER_PATTERN + "(?:" + ORDER_SEPARATOR + ORDER_PATTERN + ")*)$")
    public void add_ordered_entities_to_variable(Guard guard, String name, Type type, String orders) {
        guard.in(objects, () -> {
            CrudRepository<Object, ?> repository = springBackend.getRepositoryForEntity(type);
            addOrderedContent(name, repository, parseSort(orders));
        });
    }

    @SuppressWarnings("unchecked")
    private <E> void addOrderedContent(String name, CrudRepository<E, ?> repository, Sort sort) {
        if (repository instanceof PagingAndSortingRepository sortingRepository) {
            objects.add(name, StreamSupport.stream(sortingRepository.findAll(sort).spliterator(), false).toList());
        } else {
            throw new AssertionError(repository.getClass() + " is not a PagingAndSortingRepository!");
        }
    }

    @NotNull
    private Sort parseSort(String orders) {
        return Sort.by(Arrays.stream(orders.split(ORDER_SEPARATOR)).map(this::parseOrder).toList());
    }

    private Sort.Order parseOrder(String propertyAndDirection) {
        String[] elmts = propertyAndDirection.split(" ");
        return elmts.length > 1 ? new Sort.Order(Sort.Direction.fromString(elmts[1]), elmts[0]) : Sort.Order.by(elmts[0]);
    }

    // --- Deprecated API kept for backward compatibility with the pre-split tzatziki-spring-jpa ---

    /** @deprecated use {@link DatabaseSteps#the_table_will_contain} */
    @Deprecated(forRemoval = true)
    public void the_table_will_contain(Guard guard, String table, InsertionMode insertionMode, Object content) {
        databaseSteps.the_table_will_contain(guard, table, insertionMode, content);
    }

    /** @deprecated use {@link JpaSteps#the_entities_will_contain} */
    @Deprecated(forRemoval = true)
    public void the_entities_will_contain(Guard guard, Type type, InsertionMode insertionMode, Object content) {
        jpaSteps.the_entities_will_contain(guard, type, insertionMode, content);
    }

    /** @deprecated use {@link DatabaseSteps#enable_triggers} */
    @Deprecated(forRemoval = true)
    public void enable_triggers(Guard guard, String action) {
        databaseSteps.enable_triggers(guard, action);
    }

    /** @deprecated use {@link DatabaseSteps#the_table_contains} */
    @Deprecated(forRemoval = true)
    public void the_table_contains(Guard guard, String table, Comparison comparison, Object content) {
        databaseSteps.the_table_contains(guard, table, comparison, content);
    }

    /** @deprecated use {@link JpaSteps#the_entities_contain} */
    @Deprecated(forRemoval = true)
    public void the_entities_contain(Guard guard, Type type, Comparison comparison, Object content) {
        jpaSteps.the_entities_contain(guard, type, comparison, content);
    }

    /** @deprecated use {@link DatabaseSteps#the_table_contains_nothing} */
    @Deprecated(forRemoval = true)
    public void the_table_contains_nothing(Guard guard, String table) {
        databaseSteps.the_table_contains_nothing(guard, table);
    }

    /** @deprecated use {@link JpaSteps#the_entities_contain_nothing} */
    @Deprecated(forRemoval = true)
    public void the_entities_contain_nothing(Guard guard, Type type) {
        jpaSteps.the_entities_contain_nothing(guard, type);
    }

    /** @deprecated use {@link DatabaseSteps#add_table_content_to_variable} */
    @Deprecated(forRemoval = true)
    public void add_table_content_to_variable(Guard guard, String name, String table) {
        databaseSteps.add_table_content_to_variable(guard, name, table);
    }

    /** @deprecated use {@link JpaSteps#add_entities_to_variable} */
    @Deprecated(forRemoval = true)
    public void add_entities_to_variable(Guard guard, String name, Type type) {
        jpaSteps.add_entities_to_variable(guard, name, type);
    }

    /** @deprecated use the "the X repository will contain" step */
    @Deprecated(forRemoval = true)
    public <E> void the_repository_will_contain(Guard guard, CrudRepository<E, ?> repository, InsertionMode insertionMode, String entities) {
        the_repository_will_contain_with_type(guard, repository, insertionMode, getEntityType(repository), entities);
    }

    /** @deprecated use the "the X repository will contain" step */
    @Deprecated(forRemoval = true)
    public <E> void the_repository_will_contain_with_type(Guard guard, CrudRepository<E, ?> repository, InsertionMode insertionMode, Type entityType, String entities) {
        guard.in(objects, () -> insertIntoRepository(repository, insertionMode, entityType, entities));
    }

    /** @deprecated use the "the X repository contains" step */
    @Deprecated(forRemoval = true)
    public <E> void the_repository_contains(Guard guard, CrudRepository<E, ?> repository, Comparison comparison, String entities) {
        assertRepositoryContains(guard, repository, comparison, entities);
    }

    private <E> void assertRepositoryContains(Guard guard, CrudRepository<E, ?> repository, Comparison comparison, String entities) {
        guard.in(objects, () -> {
            List<Map> expectedEntities = Mapper.readAsAListOf(entities, Map.class);
            Class<E> entityClass = getEntityType(repository);
            List<E> actualEntities = entityClass != null && springBackend.getManagedEntityClasses().contains(entityClass)
                    ? springBackend.findAllWithExpectedFields(entityClass, expectedEntities)
                    : StreamSupport.stream(repository.findAll().spliterator(), false).toList();
            comparison.compare(actualEntities, expectedEntities);
        });
    }

    /** @deprecated use the "X is the Y entities" step */
    @Deprecated(forRemoval = true)
    public <E> void add_repository_content_to_variable(Guard guard, String name, CrudRepository<E, ?> repository) {
        guard.in(objects, () -> objects.add(name, StreamSupport.stream(repository.findAll().spliterator(), false).toList()));
    }

    /** @deprecated use the "X is the Y entities ordered by" step */
    @Deprecated(forRemoval = true)
    public <E> void add_ordered_repository_content_to_variable(Guard guard, String name, CrudRepository<E, ?> repository, Sort sort) {
        guard.in(objects, () -> addOrderedContent(name, repository, sort));
    }

    /** @deprecated use {@link SpringJpaBackend#getRepositoryForEntity} with {@link SpringJpaBackend#resolveEntityType} */
    @Deprecated(forRemoval = true)
    public <E> CrudRepository<E, ?> getRepositoryForTable(String table) {
        return springBackend.getRepositoryForEntity(springBackend.resolveEntityType(table));
    }

    /** @deprecated use {@link SpringJpaBackend#getRepositoryForEntity} */
    @Deprecated(forRemoval = true)
    public <E> CrudRepository<E, ?> getRepositoryForEntity(Type type) {
        return springBackend.getRepositoryForEntity(type);
    }

    /** @deprecated use {@link SpringJpaBackend#getRepositoryByType} */
    @Deprecated(forRemoval = true)
    public <E> CrudRepository<E, ?> getRepositoryByType(Type type) {
        return springBackend.getRepositoryByType(type);
    }

    public <E> Class<E> getEntityType(CrudRepository<E, ?> repository) {
        return Types.rawTypeArgumentOf(repository.getClass().getInterfaces()[0].getGenericInterfaces()[0]);
    }
}
```

## File: tzatziki-spring-jpa/src/test/resources/com/decathlon/tzatziki/steps/spring-jpa.feature
```
Feature: to interact with a spring boot service having a persistence layer

  Scenario: we can query a spring app and manipulate the database states using the table names
    Given that the users table will contain:
      | firstName | lastName |
      | Darth     | Vader    |
    When we call "/users/1"
    Then we receive:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      """
    And the users table contains:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
    But if we delete "/users/1"
    Then the users table contains nothing
    And calling "/users/1" returns a status NOT_FOUND_404

  Scenario: we can query a spring app using and manipulate the database states using the repository names
    Given that the UserDataSpringRepository repository will contain:
      """yml
      - firstName: Darth
        lastName: Vader
      """
    And when we call "/users/1"
    Then we receive:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      """
    And the UserDataSpringRepository repository contains:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      """
    But if we delete "/users/1"
    Then the UserDataSpringRepository repository contains nothing

  Scenario: the repository step supports the "only" insertion mode
    Given that the UserDataSpringRepository repository will contain:
      """yml
      - firstName: Darth
        lastName: Vader
      """
    And that the UserDataSpringRepository repository will contain only:
      """yml
      - firstName: Luke
        lastName: Skywalker
      """
    Then the UserDataSpringRepository repository contains only:
      """yml
      - id: 1
        firstName: Luke
        lastName: Skywalker
      """

  Scenario: the deprecated SpringJPASteps Java API is still usable from custom glue code
    Given the legacy API inserts into the users table:
      """yml
      - firstName: Darth
        lastName: Vader
      """
    And the legacy API inserts into the users table only:
      """yml
      - firstName: Luke
        lastName: Skywalker
      """
    Then the legacy API asserts that the users table contains only:
      """yml
      - id: 1
        firstName: Luke
      """

  Scenario: the deprecated table and entity delegates of SpringJPASteps still work
    Then the legacy API table and entity delegates work
    And legacyTable.size is equal to 2
    And legacyEntities.size is equal to 2
    And legacyRepository.size is equal to 2
    And legacyOrdered contains only and in order:
      | firstName |
      | Han       |
      | Darth     |

  Scenario: the repository step disables the triggers during insertion
    Given that the UserDataSpringRepository repository will contain:
      """yml
      - firstName: Darth
        lastName: Vader
        updatedAt: 2020-01-01T00:00:00Z
      """
    Then the users table contains:
      """yml
      - firstName: Darth
        updatedAt: 2020-01-01T00:00:00Z
      """

  Scenario: we can query a spring app using and manipulate the database states using the Entity names
    Given that the User entities will contain:
      """yml
      - firstName: Darth
        lastName: Vader
      """

    And when we call "/users/1"
    Then we receive:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      """
    And the User entities contain:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      """
    But if we delete "/users/1"
    Then the User entities contain nothing

  Scenario: we can control if the table contains at least or only some entities
    Given that the User entities will contain:
      | firstName | lastName |
      | Darth     | Vader    |
    And that the User entities will contain at least:
      | firstName | lastName |
      | Han       | Solo     |

    And when we call "/users"
    Then we receive only:
      """yml
      - id: 1
        firstName: Darth
        lastName: Vader
        birthDate: null
        updatedAt: null
        group: null
      - id: 2
        firstName: Han
        lastName: Solo
        birthDate: null
        updatedAt: null
        group: null
      """
    But when the User entities will contain only:
      | firstName | lastName |
      | Han       | Solo     |
    Then calling "/users" returns exactly:
      """yml
      - id: 1
        firstName: Han
        lastName: Solo
        birthDate: null
        updatedAt: null
        group: null
      """
    And the users table contains only:
      """yml
      - id: 1
        firstName: Han
      """

  Scenario: we can assert that a column is null using implicitely an anonymous object if the content matches a flag
    Given that the users table will contain:
      | firstName | lastName |
      | Darth     | Vader    |
    Then the users table contains:
      | id | birthDate |
      | 1  | ?isNull   |
    And the users table contains:
      """yml
      - id: 1
        birthDate: ?isNull
      """
    And the users table contains:
      """json
      {"id": 1, "birthDate": "?isNull"}
      """

  Scenario: we can disable or enable triggers so that we can insert the test data we want
    When the users table will contain:
      """yml
      - firstName: Darth
        lastName: Vador updated
        updatedAt: 2020-01-01T00:00:00Z
      """
    Then the users table contains:
      """yml
      - firstName: Darth
        lastName: Vador updated
        updatedAt: 2020-01-01T00:00:00Z
      """
    But if the triggers are enabled
    And that the users table will contain:
      """yml
      - firstName: Darth
        lastName: Vador updated a second time
        updatedAt: 2020-01-01T00:00:00Z
      """
    Then the users table contains:
      """yml
      - firstName: Darth
        lastName: Vador updated a second time
        updatedAt: ?after {{{[@1 second ago]}}}
      """

  Scenario: triggers are re-enabled even if an insertion fails
    Given that a java.lang.Exception is thrown when the users table will contain:
      | firstName |
      | Darth     |
    But if the triggers are enabled
    And that the users table will contain:
      | firstName | lastName | updatedAt            |
      | Luke      | Skywalker | 2020-01-01T00:00:00Z |
    Then the users table contains:
      | firstName | updatedAt                       |
      | Luke      | ?after {{{[@1 second ago]}}} |

  Scenario: we can handle the fact that an entity has a lazy field
    Given that the groups table will contain:
      | name |
      | Sith |
    And that the users table will contain:
      | firstName | lastName | group.id |
      | Darth     | Vader    | 1        |
    Then the groups table contains:
      | id | name |
      | 1  | Sith |
    And the UserDataSpringRepository repository contains:
      """yml
      - firstName: Darth
        group:
          name: Sith
      """

  Scenario: we can get a table content
    Given that the users table will contain only:
      | firstName | lastName |
      | Darth     | Vader    |
      | Han       | Solo     |
    Then usersTableContent is the users table content
    And usersTableContent.size is equal to 2
    And usersTableContent contains only:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
      | 2  | Han       | Solo     |

  Scenario: we can get entities
    Given that the User entities will contain only:
      | firstName | lastName |
      | Darth     | Vader    |
      | Han       | Solo     |
    Then userEntities is the User entities
    And userEntities.size is equal to 2
    And userEntities contains only:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
      | 2  | Han       | Solo     |

  Scenario: we can get a table content ordered
    Given that the users table will contain only:
      | firstName | lastName | birthDate                                         | updatedAt    |
      | Darth     | Vader    | {{{[@41 years before The 19th of october 1977]}}} | {{{[@now]}}} |
      | Han       | Solo     | {{{[@32 years before The 19th of october 1977]}}} | {{{[@now]}}} |
    Then usersTableContent is the users table content ordered by lastName
    And usersTableContent contains only and in order:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |
      | 1  | Darth     | Vader    |
    Then usersTableContent is the users table content ordered by birthDate
    And usersTableContent contains only and in order:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
      | 2  | Han       | Solo     |
    Then usersTableContent is the users table content ordered by birthDate desc
    And usersTableContent contains only and in order:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |
      | 1  | Darth     | Vader    |
    Then usersTableContent is the users table content ordered by updatedAt and birthDate desc
    And usersTableContent contains only and in order:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |
      | 1  | Darth     | Vader    |

  Scenario: we can get entities ordered
    Given that the User entities will contain only:
      | firstName | lastName | birthDate                                         | updatedAt    |
      | Darth     | Vader    | {{{[@41 years before The 19th of october 1977]}}} | {{{[@now]}}} |
      | Han       | Solo     | {{{[@32 years before The 19th of october 1977]}}} | {{{[@now]}}} |
    Then userEntities is the User entities ordered by lastName
    And userEntities contains only and in order:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |
      | 1  | Darth     | Vader    |
    Then userEntities is the User entities ordered by birthDate
    And userEntities contains only and in order:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
      | 2  | Han       | Solo     |
    Then userEntities is the User entities ordered by birthDate desc
    And userEntities contains only and in order:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |
      | 1  | Darth     | Vader    |
    Then userEntities is the User entities ordered by updatedAt and birthDate desc
    And userEntities contains only and in order:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |
      | 1  | Darth     | Vader    |

  Scenario: there shouldn't be any "within" implicit guard in JPA assertions
    Given that after 100ms the User entities will contain only:
      | firstName | lastName |
      | Darth     | Vader    |
    Then it is not true that the User table contains:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
    But within 150ms the User table contains:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |

    # empty the User table
    And if after 100ms the User table will contain only:
      | id | firstName | lastName |
    Then it is not true that the User table contains nothing
    But within 150ms the User table contains nothing

  Scenario: default value should still be asserted if they are present in the assertion (eg: false boolean)
    Given the users table will contain:
      | firstName | lastName |
      | Darth     | Vader    |
    Given that the groups table will contain:
    """
    name: toto_group
    users:
    - id: 1
    """
    Then it is not true that the groups table contains:
    """
    id: 1
    name: null
    users: []
    """
    Given that the evilness table will contain:
      | evil |
      | true |
    Then it is not true that the evilness table contains:
      | id | evil  |
      | 1  | false |

  Scenario: we can use extended entities and manage their tables (ex. super_users extends users)
    Given the super_users table will contain:
      | firstName | lastName  | role  |
      | Darth     | Vader     | admin |
      | Anakin    | Skywalker | dummy |
    Then the super_users table contains:
      | id | firstName | lastName  | role            |
      | 1  | Darth     | Vader     | superUser_admin |
      | 2  | Anakin    | Skywalker | superUser_dummy |

  Scenario: if we have a table which is handled by multiple entities, we should prioritize entity types from default parser package
    # the default entity knows "evil", while the non-default entity would ignore it
    Given that a tools.jackson.core.JacksonException is thrown when the evilness table will contain:
      | evil   |
      | [true] |
    And the evilness table will contain:
      | evil |
      | true |
    Then the evilness table contains only:
      | id | evil |
      | 1  | true |
    # the non-default package was not inserted
    And it is not true that the evilness table contains:
      | badAttribute |
      | true         |


  Scenario: we can manipulate tables from different schemas and jdbc/jpa repositories at the same time
    Given that the books table will contain:
      | title        |
      | Harry Potter |

    And that the products table will contain only:
      | name     |
      | computer |

    Then the books table contains exactly:
      | id | title        |
      | 1  | Harry Potter |

    And the products table contains exactly:
      | id | name     |
      | 1  | computer |

  Scenario: we can insert data into parent & child tables
    Given that the visibility table will contain:
      | name    |
      | private |
      | public  |
    And that the groups table will contain:
      | name   | visibility.id |
      | admins | 1             |
      | guests | 2             |
    And the users table will contain:
      | firstName | lastName | group.id |
      | Chuck     | Norris   | 1        |
      | Uma       | Thurman  | 2        |
      | Jackie    | Chan     | 2        |
    Then the groups table contains:
      | id | name   |
      | 1  | admins |
      | 2  | guests |
    And the users table contains:
      | id | firstName | lastName | group.id | group.name | group.visibility.name |
      | 1  | Chuck     | Norris   | 1        | admins     | private               |
      | 2  | Uma       | Thurman  | 2        | guests     | public                |
      | 3  | Jackie    | Chan     | 2        | guests     | public                |
    
  Scenario: inserts go through the Spring Data repository when one exists for the entity
    Given that the visibility table will contain:
      | name    |
      | private |
    And that the Visibility entities will contain:
      | name   |
      | public |
    And that the VisibilityDataSpringRepository repository will contain:
      | name     |
      | internal |
    Then the VisibilityDataSpringRepository saveAll method has been called 3 times
    And the visibility table contains only:
      | id | name     |
      | 1  | private  |
      | 2  | public   |
      | 3  | internal |

  Scenario: the table step can assert default values (false, 0, null)
    Given that the evilness table will contain:
      | evil  |
      | false |
    And that the users table will contain:
      | firstName | lastName |
      | Darth     | Vader    |
    Then the evilness table contains only:
      | id | evil  |
      | 1  | false |
    And the users table contains only:
      | id | firstName | birthDate | group |
      | 1  | Darth     | null      | null  |

  Scenario: tables without JPA entity are handled through JDBC
    Given that the audit_logs table will contain:
      | message | success |
      | created | true    |
      | failed  | false   |
    Then the audit_logs table contains only:
      | id | message | success |
      | 1  | created | true    |
      | 2  | failed  | false   |
    But when the audit_logs table will contain only:
      | message |
      | retried |
    Then the audit_logs table contains only:
      | id | message | success |
      | 1  | retried | null    |
    And audit is the audit_logs table content
    And audit[0].message is equal to "retried"

  Scenario: all schemas are cleared before each scenario

    Then the books table contains nothing

    Then the products table contains nothing
```
