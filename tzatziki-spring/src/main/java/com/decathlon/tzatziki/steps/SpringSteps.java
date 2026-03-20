package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.JacksonMapper;
import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Guard.always;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("java:S100") // Allow method names with underscores for BDD steps
public class SpringSteps {

    private final ObjectSteps objects;
    private final HttpSteps http;
    @Autowired
    private ApplicationContext applicationContext;
    @Autowired(required = false)
    private List<CacheManager> cacheManagers;
    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private ThreadPoolTaskExecutor taskExecutor;
    
    @Value("${local.server.port:#{null}}")
    private Integer localServerPort;

    public static boolean copyNamingStrategyFromSpringMapper = true;
    public static boolean clearThreadPoolExecutor = false;

    public SpringSteps(ObjectSteps objects, HttpSteps http) {
        this.objects = objects;
        this.http = http;
    }

    public ApplicationContext applicationContext() {
        return applicationContext;
    }

    @Before(order = -1)
    public void before() {
        if(Objects.nonNull(localServerPort)){
            http.setRelativeUrlRewriter(path -> "http://localhost:%s%s".formatted(localServerPort, path));
        }
        if (applicationContext != null) {
            we_clear_all_the_caches(always());

            if (copyNamingStrategyFromSpringMapper) {
                if (Objects.nonNull(objectMapper)) {
                    // We found a Jackson 3 ObjectMapper in the context (unlikely in standard Spring Boot 2/3 apps)
                    // We can use it directly if needed, but for now we assume we want to sync PropertyNamingStrategy
                    JacksonMapper.with(mapper -> mapper.rebuild().propertyNamingStrategy(objectMapper.serializationConfig().getPropertyNamingStrategy()).build());
                } else {
                    // Try to find a Jackson 2 ObjectMapper by name or type
                    try {
                        Object potentialMapper = applicationContext.getBean("objectMapper");
                        if (potentialMapper.getClass().getName().startsWith("com.fasterxml.jackson.databind.ObjectMapper")) {
                            //if it's a Jackson 2 mapper -> extract the strategy using reflection
                            java.lang.reflect.Method getSerializationConfig = potentialMapper.getClass().getMethod("getSerializationConfig");
                            Object serializationConfig = getSerializationConfig.invoke(potentialMapper);
                            java.lang.reflect.Method getStrategy = serializationConfig.getClass().getMethod("getPropertyNamingStrategy");
                            Object strategy = getStrategy.invoke(serializationConfig);

                            if (strategy != null) {
                                String strategyName = strategy.toString();
                                tools.jackson.databind.PropertyNamingStrategy targetStrategy = null;

                                // Map known strategies
                                if (strategyName.contains("SnakeCase")) {
                                    targetStrategy = tools.jackson.databind.PropertyNamingStrategies.SNAKE_CASE;
                                } else if (strategyName.contains("UpperCamelCase")) {
                                    targetStrategy = tools.jackson.databind.PropertyNamingStrategies.UPPER_CAMEL_CASE;
                                } else if (strategyName.contains("LowerCamelCase")) {
                                    targetStrategy = tools.jackson.databind.PropertyNamingStrategies.LOWER_CAMEL_CASE;
                                } else if (strategyName.contains("KebabCase")) {
                                    targetStrategy = tools.jackson.databind.PropertyNamingStrategies.KEBAB_CASE;
                                } else if (strategyName.contains("LowerDotCase")) {
                                    targetStrategy = tools.jackson.databind.PropertyNamingStrategies.LOWER_DOT_CASE;
                                }

                                if (targetStrategy != null) {
                                    final tools.jackson.databind.PropertyNamingStrategy finalStrategy = targetStrategy;
                                    JacksonMapper.with(mapper -> mapper.rebuild().propertyNamingStrategy(finalStrategy).build());
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore - no Jackson 2 mapper found or compatible
                    }
                }
                // not thread-safe but it's a test setup static configuration:
                copyNamingStrategyFromSpringMapper = false; // NOSONAR
            }

            objects.add("_application", applicationContext);
        }

        if(clearThreadPoolExecutor && taskExecutor != null) {
            taskExecutor.initialize();
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

    @After(order = 10001)
    public void after() {
        if(clearThreadPoolExecutor && taskExecutor != null) {
            taskExecutor.shutdown();
        }
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
