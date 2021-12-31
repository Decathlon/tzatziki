package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.User;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class MapperTest {

    @Test
    public void testDefaultMappingSpecifyType() {
        User user = Mapper.read("""
                id: 1
                name: DVador
                score: 100
                """, User.class);

        Assert.assertEquals(User.builder().id(1).name("DVador").score(100).build(), user);
    }

    @Test
    public void testDefaultMappingAsMap() {
        Map<String, Object> userAsMap = Mapper.read("""
                id: 1
                name: DVador
                score: 100
                """);

        Assert.assertEquals(1, userAsMap.get("id"));
        Assert.assertEquals("DVador", userAsMap.get("name"));
        Assert.assertEquals(100, userAsMap.get("score"));
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

        Assert.assertEquals(2, users.size());
        Assert.assertTrue(users.contains(User.builder().id(1).name("DVador").score(100).build()));
        Assert.assertTrue(users.contains(User.builder().id(2).name("Anakin").score(200).build()));
    }

    @Test
    public void testInlineListMapping() {
        List<Integer> inlineIntegerList = Mapper.readAsAListOf("1, 2, 5", Integer.class);

        Assert.assertEquals(3, inlineIntegerList.size());
        Assert.assertTrue(inlineIntegerList.containsAll(List.of(
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

        Assert.assertEquals(1, users.size());
        Assert.assertTrue(users.contains(User.builder().id(1).name("DVador").score(100).build()));
    }
}
