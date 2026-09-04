package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
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
@SuppressWarnings("java:S100")
public class SpringJPASteps {

    public static final String ORDER_PATTERN = "[a-zA-Z]+(?: asc| desc)?";
    public static final String ORDER_SEPARATOR = " and ";

    /**
     * Backward-compatible field: schemas to clean before each scenario.
     * Delegates to {@link JpaSteps#schemasToClean} during initialization.
     */
    public static List<String> schemasToClean = List.of("public");

    private final ObjectSteps objects;
    private final SpringSteps spring;
    private final List<LocalContainerEntityManagerFactoryBean> entityManagerFactories;
    private final List<EntityManager> entityManagers;
    private SpringJpaBackend springBackend;

    public SpringJPASteps(ObjectSteps objects, SpringSteps spring,
                          @Nullable List<LocalContainerEntityManagerFactoryBean> entityManagerFactories,
                          @Nullable List<EntityManager> entityManagers) {
        this.objects = objects;
        this.spring = spring;
        this.entityManagerFactories = entityManagerFactories;
        this.entityManagers = entityManagers;
    }

    @Before(order = 10)
    public void before() {
        // Register Spring's @Transient annotation for PersistenceUtil serialization filtering
        PersistenceUtil.registerTransientAnnotation(org.springframework.data.annotation.Transient.class);
        // Propagate schemas configuration to JpaSteps
        JpaSteps.schemasToClean = schemasToClean;
        if (springBackend == null) {
            springBackend = new SpringJpaBackend(spring.applicationContext(), entityManagerFactories, entityManagers);
        }
        springBackend.initialize();
        JpaSteps.registerBackend(springBackend);
    }

    // --- Spring CrudRepository-typed steps (not available in pure JPA) ---

    @Given(THAT + GUARD + "the " + TYPE + " repository will contain" + INSERTION_MODE + ":$")
    public void the_repository_will_contain(Guard guard, Type repositoryType, InsertionMode insertionMode, Object content) {
        guard.in(objects, () -> {
            CrudRepository<Object, ?> repository = springBackend.getRepositoryByType(repositoryType);
            Class<Object> entityClass = Types.rawTypeArgumentOf(repository.getClass().getInterfaces()[0].getGenericInterfaces()[0]);
            List<Object> entities = Mapper.readAsAListOf(objects.resolve(content), entityClass);
            repository.saveAll(entities);
        });
    }

    @Then(THAT + GUARD + "the " + TYPE + " repository (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_repository_contains(Guard guard, Type type, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            CrudRepository<Object, ?> repository = springBackend.getRepositoryByType(type);
            List<Object> actualEntities = StreamSupport.stream(repository.findAll().spliterator(), false).toList();
            List<Map> expectedEntities = Mapper.readAsAListOf(objects.resolve(content), Map.class);
            comparison.compare(actualEntities, expectedEntities);
        });
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
}
