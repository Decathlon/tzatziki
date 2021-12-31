package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Asserts;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.restassured.RestAssured;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static com.decathlon.tzatziki.utils.Asserts.awaitUntilAsserted;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Guard.always;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static org.junit.Assert.assertNotNull;

public class SpringSteps {

    private final ObjectSteps objects;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired(required = false)
    private List<CacheManager> cacheManagers;
    @LocalServerPort
    private int localServerPort;

    public SpringSteps(ObjectSteps objects) {
        this.objects = objects;
    }

    public ApplicationContext applicationContext() {
        return applicationContext;
    }

    @Before
    public void before() {
        RestAssured.port = localServerPort;
        if (applicationContext != null) {
            we_clear_all_the_caches(always());
        }
    }

    @Given(THAT + GUARD + A_USER + "clears? all the caches$")
    public void we_clear_all_the_caches(Guard guard) {
        clear_caches(guard, s -> true);
    }

    @Given(THAT + GUARD + A_USER + "clears? the " + VARIABLE + " cache$")
    public void we_clear_the_cache(Guard guard, String name) {
        clear_caches(guard, name::equals);
    }

    private void clear_caches(Guard guard, Predicate<String> cachePredicate) {
        guard.in(objects, () -> {
            if (cacheManagers != null) {
                cacheManagers.forEach(cacheManager -> cacheManager.getCacheNames().stream()
                        .filter(cachePredicate)
                        .map(cacheManager::getCache).filter(Objects::nonNull).forEach(Cache::invalidate));
            }
        });
    }

    @Then(THAT + GUARD + "the cache " + VARIABLE + " contains:$")
    public void theCacheContains(Guard guard, String cacheName, Object message) {
        Cache cache = getCache(cacheName);
        guard.in(objects, () -> Mapper.<Map<String, Object>>read(this.objects.resolve(message))
                .forEach((key, value) -> awaitUntilAsserted(() -> Asserts.equalsInAnyOrder(cache.get(key, Object.class), value))));
    }

    @Given(THAT + GUARD + "the cache " + VARIABLE + " will contain:$")
    public void theCacheWillContain(Guard guard, String cacheName, Object message) {
        Cache cache = getCache(cacheName);
        guard.in(objects, () -> Mapper.<Map<String, Object>>read(this.objects.resolve(message)).forEach(cache::put));
    }

    @NotNull
    private Cache getCache(String cacheName) {
        assertNotNull(cacheManagers);
        return cacheManagers.stream()
                .map(cacheManager -> cacheManager.getCache(cacheName)).filter(Objects::nonNull)
                .findAny()
                .orElseThrow(() -> new AssertionError("cache %s is missing".formatted(cacheName)));
    }

}
