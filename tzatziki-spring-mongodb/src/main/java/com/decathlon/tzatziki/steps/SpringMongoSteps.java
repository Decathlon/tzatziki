package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.CrudRepository;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.InsertionMode.INSERTION_MODE;
import static com.decathlon.tzatziki.utils.Patterns.THAT;
import static com.decathlon.tzatziki.utils.Patterns.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

public class SpringMongoSteps {
    public static boolean autoclean = true;

    private Map<Class<?>, MongoRepository<?, ?>> mongoRepositoryByClass;
    private Map<String, Class<?>> entityClassByCollectionName;

    static {
        DynamicTransformers.register(InsertionMode.class, InsertionMode::parse);
    }

    @Autowired(required = false)
    private MongoDatabaseFactory mongoDbFactory;

    private final ObjectSteps objects;
    private final SpringSteps spring;

    public SpringMongoSteps(ObjectSteps objects, SpringSteps spring) {
        this.objects = objects;
        this.spring = spring;
    }

    @Before
    public void before() {
        if (mongoRepositoryByClass == null) {
            initializeRepositories();
        }

        if (autoclean) {
            mongoRepositoryByClass().values().forEach(CrudRepository::deleteAll);
        }

        if (entityClassByCollectionName == null) {
            initializeEntityClasses();
        }

    }

    private void initializeRepositories() {
        mongoRepositoryByClass = spring.applicationContext().getBeansOfType(MongoRepository.class).values()
                .stream()
                .map(mongoRepository -> {
                    Class<?> entityClass = TypeUtils.getTypeArguments(mongoRepository.getClass(), MongoRepository.class)
                            .get(MongoRepository.class.getTypeParameters()[0]).getClass();
                    return Map.entry(entityClass, mongoRepository);
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> v1
                ));
    }

    private void initializeEntityClasses() {
        entityClassByCollectionName = mongoRepositoryByClass.keySet().stream()
                .sorted((c1, c2) -> {
                    if (TypeParser.defaultPackage == null) return 0;
                    if (c1.getPackageName().startsWith(TypeParser.defaultPackage)) return -1;
                    return c2.getPackageName().startsWith(TypeParser.defaultPackage) ? 1 : 0;
                })
                .map(clazz -> Map.entry(getCollectionName(clazz), clazz))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (t1, t2) -> t1
                ));
    }


    private Map<Class<?>, MongoRepository<?, ?>> mongoRepositoryByClass() {
        return mongoRepositoryByClass;
    }

    private String getCollectionName(Class<?> clazz) {
        Document document = clazz.getAnnotation(Document.class);
        if (document != null && !document.collection().isEmpty()) {
            return document.collection();
        }
        return clazz.getSimpleName();
    }

    @Given(THAT + GUARD + "the ([^ ]+) document will contain" + INSERTION_MODE + ":$")
    public void the_document_will_contain(Guard guard, String document, InsertionMode insertionMode, Object content) {
        the_repository_will_contain(guard, getRepositoryForDocument(document), insertionMode, objects.resolve(content));
    }

    @Then(THAT + GUARD + "the ([^ ]+) document (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_document_contains(Guard guard, String document, Comparison comparison, Object content) {
        the_repository_contains(guard, getRepositoryForDocument(document), comparison, objects.resolve(content));
    }

    @Then(THAT + GUARD + "the ([^ ]+) document (?:still )?contains nothing$")
    public void the_document_contains_nothing(Guard guard, String document) {
        the_repository_contains_nothing(guard, getRepositoryForDocument(document));
    }

    @Given(THAT + GUARD + "the " + TYPE + " repository will contain" + INSERTION_MODE + ":$")
    public void the_repository_will_contain(Guard guard, Type repositoryType, InsertionMode insertionMode, Object content) {
        the_repository_will_contain(guard, getRepositoryByType(repositoryType), insertionMode, objects.resolve(content));
    }

    @Then(THAT + GUARD + "the " + TYPE + " repository (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_repository_contains(Guard guard, Type type, Comparison comparison, Object content) {
        the_repository_contains(guard, getRepositoryByType(type), comparison, objects.resolve(content));
    }

    @Then(THAT + GUARD + "the " + TYPE + " repository (?:still )?contains nothing$")
    public void the_repository_contains_nothing(Guard guard, Type type) {
        the_repository_contains_nothing(guard, getRepositoryByType(type));
    }

    @Given(THAT + GUARD + "the " + TYPE + " entities will contain" + INSERTION_MODE + ":$")
    public void the_entities_will_contain(Guard guard, Type type, InsertionMode insertionMode, Object content) {
        the_repository_will_contain(guard, getRepositoryForDocument(type), insertionMode, objects.resolve(content));
    }

    @Then(THAT + GUARD + "the " + TYPE + " entities (?:still )?contain" + COMPARING_WITH + ":$")
    public void the_entities_contain(Guard guard, Type type, Comparison comparison, Object content) {
        the_repository_contains(guard, getRepositoryForDocument(type), comparison, objects.resolve(content));
    }

    @Then(THAT + GUARD + "the " + TYPE + " entities (?:still )?contain nothing$")
    public void the_entities_contain_nothing(Guard guard, Type type) {
        the_repository_contains_nothing(guard, getRepositoryForDocument(type));
    }

    public <E> void the_repository_will_contain(Guard guard, CrudRepository<E, ?> repository, InsertionMode insertionMode, String entities) {
        guard.in(objects, () -> {
            Class<E> entityType = getEntityType(repository);
            if (insertionMode == InsertionMode.ONLY) {
                String document = entityType.getAnnotation(Document.class).value();
                new MongoTemplate(mongoDbFactory).dropCollection(document);
            }
            repository.saveAll(Mapper.readAsAListOf(entities, entityType));
        });
    }

    public <E> void the_repository_contains(Guard guard, CrudRepository<E, ?> repository, Comparison comparison, String entities) {
        guard.in(objects, () -> {
            List<E> actualEntities = StreamSupport.stream(repository.findAll().spliterator(), false).collect(Collectors.toList());
            List<Map> expectedEntities = Mapper.readAsAListOf(entities, Map.class);
            comparison.compare(actualEntities, expectedEntities);
        });
    }

    private void the_repository_contains_nothing(Guard guard, CrudRepository<Object, ?> repositoryOfEntity) {
        guard.in(objects, () -> assertThat(repositoryOfEntity.count()).isZero());
    }

    public <E> CrudRepository<E, ?> getRepositoryForDocument(String document) {

        return spring.applicationContext()
                .getBeansOfType(CrudRepository.class)
                .values()
                .stream()
                .map(bean -> (CrudRepository<E, ?>) bean).filter(r -> {
                    Class<E> e = getEntityType(r);
                    return (e.isAnnotationPresent(Document.class) && e.getAnnotation(Document.class).value().equals(document)) || e.getSimpleName().equals(document);
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("there was no CrudRepository found for the document '%s'! If you don't need one in your app, you must create one in your tests!".formatted(document)));
    }

    public <E> CrudRepository<E, ?> getRepositoryForDocument(Type type) {
        if (Types.rawTypeOf(type).isAnnotationPresent(Document.class)) {
            return spring.applicationContext()
                    .getBeansOfType(CrudRepository.class)
                    .values()
                    .stream()
                    .map(bean -> (CrudRepository<E, ?>) bean)
                    .filter(r -> type.equals(TypeUtils.unrollVariables(TypeUtils.getTypeArguments(r.getClass(), CrudRepository.class), CrudRepository.class.getTypeParameters()[0])))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("there was no CrudRepository found for document %s! If you don't need one in your app, you must create one in your tests!".formatted(type.getTypeName())));
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

}
