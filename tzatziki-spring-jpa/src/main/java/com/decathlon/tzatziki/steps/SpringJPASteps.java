package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
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
public class SpringJPASteps extends DatabaseConfiguration {

    public static final String ORDER_PATTERN = "[a-zA-Z]+(?: asc| desc)?";
    public static final String ORDER_SEPARATOR = " and ";

    private final ObjectSteps objects;
    private final SpringSteps spring;
    private final DatabaseSteps databaseSteps;
    private final JpaSteps jpaSteps;
    private final List<LocalContainerEntityManagerFactoryBean> entityManagerFactories;
    private SpringJpaBackend springBackend;

    @Autowired
    public SpringJPASteps(ObjectSteps objects, SpringSteps spring, DatabaseSteps databaseSteps, JpaSteps jpaSteps,
                          @Nullable List<LocalContainerEntityManagerFactoryBean> entityManagerFactories) {
        this.objects = objects;
        this.spring = spring;
        this.databaseSteps = databaseSteps;
        this.jpaSteps = jpaSteps;
        this.entityManagerFactories = entityManagerFactories;
    }

    /**
     * Pre-split constructor kept for glue code instantiating or extending SpringJPASteps directly.
     * The EntityManagers are not used anymore (they are obtained from the factories), and the deprecated
     * delegates of this instance use their own {@link DatabaseSteps} and {@link JpaSteps}, so trigger toggling
     * through {@link #enable_triggers} is not shared with the Cucumber-managed {@link DatabaseSteps}.
     *
     * @deprecated use {@link #SpringJPASteps(ObjectSteps, SpringSteps, DatabaseSteps, JpaSteps, List)}
     */
    @Deprecated(forRemoval = true)
    public SpringJPASteps(ObjectSteps objects, SpringSteps spring,
                          @Nullable List<LocalContainerEntityManagerFactoryBean> entityManagerFactories,
                          @Nullable List<EntityManager> entityManagers) {
        this(objects, spring, new DatabaseSteps(objects), entityManagerFactories);
    }

    private SpringJPASteps(ObjectSteps objects, SpringSteps spring, DatabaseSteps databaseSteps,
                           @Nullable List<LocalContainerEntityManagerFactoryBean> entityManagerFactories) {
        this(objects, spring, databaseSteps, new JpaSteps(objects, databaseSteps), entityManagerFactories);
    }

    @Before(order = 10)
    public void before() {
        // Register Spring's @Transient annotation for PersistenceUtil serialization filtering
        PersistenceUtil.registerTransientAnnotation(org.springframework.data.annotation.Transient.class);
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
