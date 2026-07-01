package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Proves that when both delegates are on the classpath (jackson2 + jackson3) and the selector
 * {@code tzatziki.mapper.delegate=jackson3} is set (configured in this module's surefire fork),
 * {@link Mapper} routes to {@code Jackson3Mapper} end-to-end.
 */
public class Jackson3RoutingTest {

    @Test
    public void bothDelegatesArePresentOnTheClasspath() {
        Set<String> available = ServiceLoader.load(MapperDelegate.class).stream()
                .map(provider -> provider.type().getSimpleName())
                .collect(Collectors.toSet());

        Assertions.assertTrue(available.contains("JacksonMapper"),
                "expected the Jackson 2 delegate (JacksonMapper) on the classpath, found: " + available);
        Assertions.assertTrue(available.contains("Jackson3Mapper"),
                "expected the Jackson 3 delegate (Jackson3Mapper) on the classpath, found: " + available);
    }

    @Test
    public void selectorRoutesToJackson3Delegate() {
        Assertions.assertTrue(Mapper.isJackson3(),
                "with both delegates present and the selector set to jackson3, Mapper must route to Jackson3Mapper");
        Assertions.assertFalse(Mapper.isJackson2(),
                "the Jackson 2 delegate must not be active when the selector routes to jackson3");
    }

    @Test
    public void mapperRoundTripGoesThroughJackson3() {
        User user = User.builder().id(1).name("DVador").score(100).build();

        String json = Mapper.toJson(user);
        User roundTripped = Mapper.read(json, User.class);

        Assertions.assertEquals(user, roundTripped);
    }
}
