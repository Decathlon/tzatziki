package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.JacksonMapper;
import com.decathlon.tzatziki.utils.Mapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Guard.always;
import static com.decathlon.tzatziki.utils.Patterns.A_USER;
import static com.decathlon.tzatziki.utils.Patterns.THAT;
import static com.decathlon.tzatziki.utils.Patterns.VARIABLE;
import static org.junit.Assert.assertNotNull;

public class SpringSteps {

    private final ObjectSteps objects;
    private final HttpSteps http;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired(required = false)
    private List<CacheManager> cacheManagers;
    @Autowired(required = false)
    private ObjectMapper objectMapper;
    @LocalServerPort
    private int localServerPort;

    public static boolean copyNamingStrategyFromSpringMapper = true;

    public SpringSteps(ObjectSteps objects, HttpSteps http) {
        this.objects = objects;
        this.http = http;
    }

    public ApplicationContext applicationContext() {
        return applicationContext;
    }

    @Before(order = -1)
    public void before() {
        http.setRelativeUrlRewriter(path -> "http://localhost:%s%s".formatted(localServerPort, path));
        if (applicationContext != null) {
            we_clear_all_the_caches(always());

            if (copyNamingStrategyFromSpringMapper) {
                JacksonMapper.with(mapper -> mapper.setPropertyNamingStrategy(objectMapper.getPropertyNamingStrategy()));
                copyNamingStrategyFromSpringMapper = false;
            }

            objects.add("_application", applicationContext);
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

    @Then(THAT + GUARD + "the cache " + VARIABLE + " contains" + COMPARING_WITH + "?:$")
    public void theCacheContains(Guard guard, String cacheName, Comparison comparison, Object message) {
        Cache cache = getCache(cacheName);
        guard.in(objects, () -> {
            Map<String, Object> expected = Mapper.read(this.objects.resolve(message));
            Map<String, Object> cacheMap = expected.keySet().stream().collect(HashMap::new, (m, k) -> m.put(k, cache.get(k, Object.class)), HashMap::putAll);
            comparison.compare(cacheMap, expected);
        });

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
