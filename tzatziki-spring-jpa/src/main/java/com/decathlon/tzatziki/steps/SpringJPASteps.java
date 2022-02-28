package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.sql.DataSource;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.decathlon.tzatziki.utils.Asserts.awaitUntilAsserted;
import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.InsertionMode.INSERTION_MODE;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static org.assertj.core.api.Assertions.assertThat;

public class SpringJPASteps {

    static {
        DynamicTransformers.register(Type.class, TypeParser::parse);
        DynamicTransformers.register(InsertionMode.class, InsertionMode::parse);
        JacksonMapper.with(objectMapper -> objectMapper.registerModule(new Hibernate5Module()));
    }

    public static boolean autoclean = true;
    public static String schemaToClean = "public";

    @Autowired(required = false)
    private List<LocalContainerEntityManagerFactoryBean> entityManagerFactories;

    private boolean disableTriggers = true;
    private final ObjectSteps objects;
    private final SpringSteps spring;

    public SpringJPASteps(ObjectSteps objects, SpringSteps spring) {
        this.objects = objects;
        this.spring = spring;
    }

    @Before
    public void before() {
        if (autoclean) {
            dataSources().forEach(dataSource -> {
                DatabaseCleaner.clean(dataSource, schemaToClean);
                DatabaseCleaner.setTriggers(dataSource, DatabaseCleaner.TriggerStatus.enable);
            });
        }
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
        the_repository_will_contain(guard, getRepositoryForTable(table), insertionMode, objects.resolve(content));
    }

    @Given(THAT + GUARD + "the " + TYPE + " entities will contain" + INSERTION_MODE + ":$")
    public void the_entities_will_contain(Guard guard, Type type, InsertionMode insertionMode, Object content) {
        the_repository_will_contain(guard, getRepositoryForEntity(type), insertionMode, objects.resolve(content));
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

    private void the_repository_contains_nothing(Guard guard, CrudRepository<Object, ?> repositoryOfEntity) {
        guard.in(objects, () -> awaitUntilAsserted(() -> assertThat(repositoryOfEntity.count()).isZero()));
    }

    public <E> void the_repository_will_contain(Guard guard, CrudRepository<E, ?> repository, InsertionMode insertionMode, String entities) {
        guard.in(objects, () -> {
            if (disableTriggers) {
                dataSources().forEach(dataSource -> DatabaseCleaner.setTriggers(dataSource, DatabaseCleaner.TriggerStatus.disable));
            }
            Class<E> entityType = getEntityType(repository);
            if (insertionMode == InsertionMode.ONLY) {
                String table = entityType.getAnnotation(Table.class).name();
                DataSource dataSource = entityManagerFactories.stream()
                        .filter(entityManagerFactory -> entityManagerFactory.getPersistenceUnitInfo() != null)
                        .filter(entityManagerFactory -> entityManagerFactory.getPersistenceUnitInfo().getManagedClassNames().contains(entityType.getName()))
                        .map(LocalContainerEntityManagerFactoryBean::getDataSource).findFirst()
                        .orElseThrow();
                new JdbcTemplate(dataSource).update("TRUNCATE %s RESTART IDENTITY CASCADE".formatted(table));
            }
            repository.saveAll(Mapper.readAsAListOf(entities, entityType));
            if (disableTriggers) {
                dataSources().forEach(dataSource -> DatabaseCleaner.setTriggers(dataSource, DatabaseCleaner.TriggerStatus.enable));
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <E> void the_repository_contains(Guard guard, CrudRepository<E, ?> repository, Comparison comparison, String entities) {
        guard.in(objects, () -> {
            Class<E> entityType = entities.matches("[\\s\\S]+:\\s*\"?\\?([\\S]+)[\\s\\S]*") ? (Class<E>) Map.class : getEntityType(repository);
            awaitUntilAsserted(() -> {
                List<E> actualEntities = StreamSupport.stream(repository.findAll().spliterator(), false).collect(Collectors.toList());
                List<?> expectedEntities = Mapper.readAsAListOf(entities, entityType);
                comparison.compare(actualEntities, expectedEntities);
            });
        });
    }

    public <E> void add_repository_content_to_variable(Guard guard, String name, CrudRepository<E, ?> repository) {
        guard.in(objects, () -> objects.add(name, StreamSupport.stream(repository.findAll().spliterator(), false).toList()));
    }

    @SuppressWarnings("unchecked")
    public <E> CrudRepository<E, ?> getRepositoryForTable(String table) {
        return spring.applicationContext().getBeansOfType(CrudRepository.class).values()
                .stream()
                .map(bean -> (CrudRepository<E, ?>) bean)
                .filter(r -> {
                    Class<E> e = getEntityType(r);
                    return (e.isAnnotationPresent(Table.class) && e.getAnnotation(Table.class).name().equals(table))
                            || e.getSimpleName().equals(table)
                            || toSnakeCase(e.getSimpleName()).equals(table);
                }).findFirst().orElseThrow(() -> new AssertionError(
                        "there was no CrudRepository found for table '%s'! If you don't need one in your app, you must create one in your tests!".formatted(table)
                ));
    }

    @SuppressWarnings({"unchecked"})
    public <E> CrudRepository<E, ?> getRepositoryForEntity(Type type) {
        if (Types.rawTypeOf(type).isAnnotationPresent(Entity.class)) {
            return spring.applicationContext().getBeansOfType(CrudRepository.class).values()
                    .stream()
                    .map(bean -> (CrudRepository<E, ?>) bean)
                    .filter(r -> type.equals(TypeUtils.unrollVariables(TypeUtils.getTypeArguments(r.getClass(), CrudRepository.class), CrudRepository.class.getTypeParameters()[0])))
                    .findFirst().orElseThrow(() -> new AssertionError("there was no CrudRepository found for entity %s! If you don't need one in your app, you must create one in your tests!".formatted(type.getTypeName())));
        }
        throw new AssertionError(type + " is not an Entity!");
    }

    public <E> CrudRepository<E, ?> getRepositoryByType(Type type) {
        if (Types.isAssignableTo(type, CrudRepository.class)) {
            return spring.applicationContext().getBean(Types.rawTypeOf(type));
        }
        throw new AssertionError(type + " is not a CrudRepository!");
    }

    public <E> Class<E> getEntityType(CrudRepository<E, ?> repository) {
        return Types.rawTypeArgumentOf(repository.getClass().getInterfaces()[0].getGenericInterfaces()[0]);
    }

    private static String toSnakeCase(String input) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ' ') {
                output.append('_');
            } else {
                if (Character.isUpperCase(c)) {
                    if (i > 0) {
                        output.append('_');
                    }
                    output.append(Character.toLowerCase(c));
                } else {
                    output.append(c);
                }
            }
        }
        return output.toString();
    }
}
