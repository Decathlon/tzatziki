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
