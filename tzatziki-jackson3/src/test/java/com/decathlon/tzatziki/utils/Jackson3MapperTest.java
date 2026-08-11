package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.User;
import com.google.common.reflect.TypeToken;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class Jackson3MapperTest {

    @Test
    public void testDefaultMappingSpecifyType() {
        User user = Mapper.read("""
                id: 1
                name: DVador
                score: 100
                """, User.class);

        Assertions.assertEquals(User.builder().id(1).name("DVador").score(100).build(), user);
    }

    @Test
    public void testDefaultMappingAsMap() {
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
    public void testListMapping() {
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
    public void testUntypedListMapping() {
        List<Integer> values = Mapper.read("""
                - 1
                - 2
                """);

        Assertions.assertEquals(List.of(1, 2), values);
    }

    @Test
    public void testParameterizedTypeMapping() {
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
    public void testInlineListMapping() {
        List<Integer> inlineIntegerList = Mapper.readAsAListOf("1, 2, 5", Integer.class);

        Assertions.assertEquals(3, inlineIntegerList.size());
        Assertions.assertTrue(inlineIntegerList.containsAll(List.of(
                1,
                2,
                5
        )));
    }

    @Test
    public void testAutoWrappingListMapping() {
        List<User> users = Mapper.readAsAListOf("""
                id: 1
                name: DVador
                score: 100
                """, User.class);

        Assertions.assertEquals(1, users.size());
        Assertions.assertTrue(users.contains(User.builder().id(1).name("DVador").score(100).build()));
    }

    @Test
    public void testToJsonAndToYaml() {
        User user = User.builder().id(1).name("DVador").score(100).build();

        String json = Mapper.toJson(user);
        Assertions.assertTrue(json.contains("\"name\":\"DVador\""));

        User roundTrip = Mapper.read(json, User.class);
        Assertions.assertEquals(user, roundTrip);
    }

    @Test
    public void testToJsonStringInputs() {
        Assertions.assertEquals("{\"id\":1}", Mapper.toJson("{\"id\":1}"));
        Assertions.assertEquals("{\"id\":1}", Mapper.toJson("id: 1"));
        Assertions.assertEquals("[1,2]", Mapper.toJson("- 1\n- 2"));
        Assertions.assertEquals("[", Mapper.toJson("["));
        Assertions.assertEquals("x", Mapper.toJson('x'));
    }

    @Test
    public void testWithConfiguresAllMappers() {
        Jackson3Mapper.with(mapper -> mapper);
    }

    @Test
    public void testToNonDefaultJson() {
        User user = User.builder().id(0).name("DVador").score(null).build();

        String json = Mapper.toNonDefaultJson(user);
        Assertions.assertTrue(json.contains("DVador"));
        Assertions.assertFalse(json.contains("score"));
        Assertions.assertFalse(json.contains("id"));
    }

    @Test
    public void testUnknownPropertiesFailDeserialization() {
        Assertions.assertThrows(UnrecognizedPropertyException.class,
                () -> Mapper.read("unknown: true", User.class));
    }
}
