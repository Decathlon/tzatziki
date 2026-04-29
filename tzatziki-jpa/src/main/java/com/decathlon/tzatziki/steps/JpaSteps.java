package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.Before;
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
 * to the JPA backend via {@link JpaDbBackend} when this module is present.
 * <p>
 * No Spring dependency — works with any JPA implementation.
 */
@SuppressWarnings("java:S100")
public class JpaSteps {

    private static volatile JpaBackend backend;

    static {
        DynamicTransformers.register(Type.class, TypeParser::parse);
        JacksonMapper.with(objectMapper -> objectMapper.registerModule(PersistenceUtil.getMapperModule()));
    }

    public static boolean autoclean = true;
    public static List<String> schemasToClean = List.of("public");

    private final ObjectSteps objects;
    private final DatabaseSteps databaseSteps;

    public JpaSteps(ObjectSteps objects, DatabaseSteps databaseSteps) {
        this.objects = objects;
        this.databaseSteps = databaseSteps;
    }

    /**
     * Register a JpaBackend implementation.
     * Called by upper layers (e.g., SpringJpaBackend @Before hook) or directly in standalone JPA tests.
     * Also registers a JpaDbBackend adapter with DatabaseSteps for table-level operations.
     */
    public static void registerBackend(JpaBackend jpaBackend) {
        backend = jpaBackend;
        // Register all datasources with DatabaseSteps for autoclean
        jpaBackend.getAllDataSources().forEach(DatabaseSteps::registerDataSource);
        // Register JPA-aware DbBackend so table-level steps in DatabaseSteps use JPA
        DatabaseSteps.registerBackend(new JpaDbBackend(jpaBackend));
    }

    public static JpaBackend getBackend() {
        return backend;
    }

    @Before(order = 50)
    public void before() {
        if (backend == null) return;
        if (autoclean) {
            backend.getAllDataSources().forEach(dataSource -> {
                DatabaseCleaner.clean(dataSource, schemasToClean);
                DatabaseCleaner.setTriggers(dataSource, schemasToClean, DatabaseCleaner.TriggerStatus.enable);
            });
        }
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
        guard.in(objects, () -> assertThat(backend.count((Class<?>) type)).isZero());
    }

    @Then(THAT + GUARD + VARIABLE + " is the " + TYPE + " entities$")
    public void add_entities_to_variable(Guard guard, String name, Type type) {
        guard.in(objects, () -> objects.add(name, backend.findAll((Class<?>) type)));
    }

    // ---- internal methods ----

    @SuppressWarnings("unchecked")
    private <E> void insertEntitiesByType(Type type, InsertionMode insertionMode, String entities) {
        if (!(type instanceof Class<?>)) return;
        Class<E> entityClass = (Class<E>) type;
        doInsert(entityClass, insertionMode, entities);
    }

    private <E> void doInsert(Class<E> entityClass, InsertionMode insertionMode, String entities) {
        if (databaseSteps.isDisableTriggers()) {
            databaseSteps.disableTriggersOnAllDataSources();
        }
        if (insertionMode == InsertionMode.ONLY) {
            backend.truncate(entityClass);
        }
        backend.saveAll(entityClass, Mapper.readAsAListOf(entities, entityClass));
        if (databaseSteps.isDisableTriggers()) {
            databaseSteps.enableTriggersOnAllDataSources();
        }
    }

    @SuppressWarnings("unchecked")
    private <E> List<E> findAllEntitiesWithOnlyExpectedFields(Class<?> entityClass, List<Map> expectedEntities) {
        Class<E> clazz = (Class<E>) entityClass;
        return backend.findAllWithExpectedFields(clazz, expectedEntities);
    }
}
