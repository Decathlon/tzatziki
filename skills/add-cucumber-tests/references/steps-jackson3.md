# User Provided Header
Tzatziki Jackson 3 module reference.
- Jackson3Mapper.java implements the MapperDelegate with Jackson 3 (`tools.jackson`) for YAML and JSON mapping.
- Jackson 3 is selected automatically when `tzatziki-jackson3` is on the classpath through Java ServiceLoader discovery.
- Tests demonstrate typed and untyped mapping, list handling, JSON/YAML conversion, mapper configuration, and delegate routing.


# Directory Structure
````
tzatziki-jackson3/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              utils/
                Jackson3Mapper.java
      resources/
        META-INF/
          services/
            com.decathlon.tzatziki.utils.MapperDelegate
    test/
      java/
        com/
          decathlon/
            tzatziki/
              utils/
                Jackson3MapperTest.java
                Jackson3RoutingTest.java
              User.java
  README.md
````

# Files

## File: tzatziki-jackson3/src/main/java/com/decathlon/tzatziki/utils/Jackson3Mapper.java
````java
package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.Lists;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLReadFeature;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class Jackson3Mapper implements MapperDelegate {

    private static ObjectMapper yaml = configure(YAMLMapper.builder()
            .enable(YAMLReadFeature.EMPTY_STRING_AS_NULL)
            .disable(YAMLWriteFeature.SPLIT_LINES)
            .build());

    private static ObjectMapper json = configure(JsonMapper.builder().build());

    private static ObjectMapper nonDefaultJson = json.rebuild()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_DEFAULT))
            .build();

    private static ObjectMapper configure(ObjectMapper mapper) {
        return mapper.rebuild()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .build();
    }

    public static void with(UnaryOperator<ObjectMapper> configurator) {
        yaml = configurator.apply(yaml);
        json = configurator.apply(json);
        nonDefaultJson = configurator.apply(nonDefaultJson);
    }

    @SuppressWarnings("unchecked")
    public <E> E read(String content) {
        if (Mapper.isList(content)) {
            return (E) yaml.readValue(content, List.class);
        }
        return (E) yaml.readValue(content, Map.class);
    }

    public <E> List<E> readAsAListOf(String content, Class<E> clazz) {
        if (Mapper.isList(content)) {
            return yaml.readValue(content, yaml.getTypeFactory().constructParametricType(List.class, clazz));
        }
        try {
            return Lists.newArrayList(yaml.readValue(content, clazz));
        } catch (JacksonException e) {
            return readAsAListOf("[%s]".formatted(content), clazz);
        }
    }

    public <E> E read(String content, Class<E> clazz) {
        return yaml.readValue(content, clazz);
    }

    public <E> E read(String content, Type type) {
        return yaml.readValue(content, toJavaType(type));
    }

    private static JavaType toJavaType(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            JavaType[] javaTypes = Stream.of(parameterizedType.getActualTypeArguments()).map(Jackson3Mapper::toJavaType).toArray(JavaType[]::new);
            return yaml.getTypeFactory().constructParametricType((Class<?>) ((ParameterizedType) type).getRawType(), javaTypes);
        }
        return yaml.getTypeFactory().constructType(type);
    }

    public String toJson(Object object) {
        return toJson(object, json);
    }

    public String toNonDefaultJson(Object object) {
        return toJson(object, nonDefaultJson);
    }

    private static String toJson(Object object, ObjectMapper objectMapper) {
        if (object instanceof String string) {
            try {
                if (Mapper.isJson(string)) {
                    return string;
                }
                if (Mapper.isList(string)) {
                    return Mapper.toJson(Mapper.read(string, List.class));
                }
                return Mapper.toJson(Mapper.read(string, Map.class));
            } catch (Exception e) {
                return string;
            }
        }

        String value = objectMapper.writeValueAsString(object);
        if (value.charAt(0) == '\"' && value.charAt(value.length() - 1) == '\"') {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    public String toYaml(Object object) {
        if (object instanceof String objectStr) {
            return objectStr;
        }
        return yaml.writeValueAsString(object).replaceAll("^---\n?", "");
    }
}
````

## File: tzatziki-jackson3/src/main/resources/META-INF/services/com.decathlon.tzatziki.utils.MapperDelegate
````
com.decathlon.tzatziki.utils.Jackson3Mapper
````

## File: tzatziki-jackson3/src/test/java/com/decathlon/tzatziki/utils/Jackson3MapperTest.java
````java
package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.User;
import com.google.common.reflect.TypeToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class Jackson3MapperTest {

    @Test
    void testDefaultMappingSpecifyType() {
        User user = Mapper.read("""
                id: 1
                name: DVador
                score: 100
                """, User.class);

        Assertions.assertEquals(User.builder().id(1).name("DVador").score(100).build(), user);
    }

    @Test
    void testDefaultMappingAsMap() {
        Map<String, Object> userAsMap = Mapper.read("""
                id: 1
                name: DVador
                score: 100
                """);

        Assertions.assertEquals(1, userAsMap.get("id"));
        Assertions.assertEquals("DVador", userAsMap.get("name"));
        Assertions.assertEquals(100, userAsMap.get("score"));
    }

    @Test
    void testListMapping() {
        List<User> users = Mapper.readAsAListOf("""
                -   id: 1
                    name: DVador
                    score: 100
                -   id: 2
                    name: Anakin
                    score: 200
                """, User.class);

        Assertions.assertEquals(2, users.size());
        Assertions.assertTrue(users.contains(User.builder().id(1).name("DVador").score(100).build()));
        Assertions.assertTrue(users.contains(User.builder().id(2).name("Anakin").score(200).build()));
    }

    @Test
    void testUntypedListMapping() {
        List<Integer> values = Mapper.read("""
                - 1
                - 2
                """);

        Assertions.assertEquals(List.of(1, 2), values);
    }

    @Test
    void testParameterizedTypeMapping() {
        Type usersType = new TypeToken<List<User>>() {
        }.getType();

        List<User> users = Mapper.read("""
                - id: 1
                  name: DVador
                  score: 100
                """, usersType);

        Assertions.assertEquals(List.of(User.builder().id(1).name("DVador").score(100).build()), users);
    }

    @Test
    void testInlineListMapping() {
        List<Integer> inlineIntegerList = Mapper.readAsAListOf("1, 2, 5", Integer.class);

        Assertions.assertEquals(3, inlineIntegerList.size());
        Assertions.assertTrue(inlineIntegerList.containsAll(List.of(
                1,
                2,
                5
        )));
    }

    @Test
    void testAutoWrappingListMapping() {
        List<User> users = Mapper.readAsAListOf("""
                id: 1
                name: DVador
                score: 100
                """, User.class);

        Assertions.assertEquals(1, users.size());
        Assertions.assertTrue(users.contains(User.builder().id(1).name("DVador").score(100).build()));
    }

    @Test
    void testToJsonAndToYaml() {
        User user = User.builder().id(1).name("DVador").score(100).build();

        String json = Mapper.toJson(user);
        Assertions.assertTrue(json.contains("\"name\":\"DVador\""));

        User roundTrip = Mapper.read(json, User.class);
        Assertions.assertEquals(user, roundTrip);
    }

    @Test
    void testToJsonStringInputs() {
        Assertions.assertEquals("{\"id\":1}", Mapper.toJson("{\"id\":1}"));
        Assertions.assertEquals("{\"id\":1}", Mapper.toJson("id: 1"));
        Assertions.assertEquals("[1,2]", Mapper.toJson("- 1\n- 2"));
        Assertions.assertEquals("[", Mapper.toJson("["));
        Assertions.assertEquals("x", Mapper.toJson('x'));
    }

    @Test
    void testWithConfiguresAllMappers() {
        AtomicInteger configuredMappers = new AtomicInteger();
        Jackson3Mapper.with(mapper -> {
            configuredMappers.incrementAndGet();
            return mapper;
        });

        Assertions.assertEquals(3, configuredMappers.get());
    }

    @Test
    void testToNonDefaultJson() {
        User user = User.builder().id(0).name("DVador").score(null).build();

        String json = Mapper.toNonDefaultJson(user);
        Assertions.assertTrue(json.contains("DVador"));
        Assertions.assertFalse(json.contains("score"));
        Assertions.assertFalse(json.contains("id"));
    }
}
````

## File: tzatziki-jackson3/src/test/java/com/decathlon/tzatziki/utils/Jackson3RoutingTest.java
````java
package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Proves that when both delegates are on the classpath (jackson2 + jackson3),
 * {@link Mapper} routes to {@code Jackson3Mapper} by default end-to-end.
 */
class Jackson3RoutingTest {

    @Test
    void bothDelegatesArePresentOnTheClasspath() {
        Set<String> available = ServiceLoader.load(MapperDelegate.class).stream()
                .map(provider -> provider.type().getSimpleName())
                .collect(Collectors.toSet());

        Assertions.assertTrue(available.contains("JacksonMapper"),
                "expected the Jackson 2 delegate (JacksonMapper) on the classpath, found: " + available);
        Assertions.assertTrue(available.contains("Jackson3Mapper"),
                "expected the Jackson 3 delegate (Jackson3Mapper) on the classpath, found: " + available);
    }

    @Test
    void defaultsToJackson3WhenBothDelegatesArePresent() {
        Assertions.assertTrue(Mapper.isJackson3(),
                "with both delegates present, Mapper must route to Jackson3Mapper");
        Assertions.assertFalse(Mapper.isJackson2(),
                "the Jackson 2 delegate must not be active when Jackson3Mapper is present");
    }

    @Test
    void mapperRoundTripGoesThroughJackson3() {
        User user = User.builder().id(1).name("DVador").score(100).build();

        String json = Mapper.toJson(user);
        User roundTripped = Mapper.read(json, User.class);

        Assertions.assertEquals(user, roundTripped);
    }
}
````

## File: tzatziki-jackson3/src/test/java/com/decathlon/tzatziki/User.java
````java
package com.decathlon.tzatziki;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    Integer id;

    String name;

    Integer score;

}
````

## File: tzatziki-jackson3/README.md
````markdown
Tzatziki Jackson 3
======

## Description

This module contains the Jackson 3 (`tools.jackson`) implementation of the `MapperDelegate` required by Tzatziki.

## Get started with this module

Add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-jackson3</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

Tzatziki will then use Jackson 3 for serialization and deserialization in your tests.

## Delegate selection

Mapper implementations are discovered through Java's `ServiceLoader`:

- Jackson 3 is selected whenever `tzatziki-jackson3` is on the classpath.
- Jackson 2 is selected when `tzatziki-jackson3` is absent and `tzatziki-jackson` is present.

When both implementations are present, Jackson 3 is selected automatically.
````
