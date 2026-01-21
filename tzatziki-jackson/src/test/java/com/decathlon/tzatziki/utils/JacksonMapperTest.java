package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class JacksonMapperTest {

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
}
