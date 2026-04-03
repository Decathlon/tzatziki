# User Provided Header
Tzatziki Spring module reference.
- SpringSteps.java defines @Given/@When/@Then patterns for Spring application context, properties, and bean manipulation.
- .feature files demonstrate valid Spring step usage.
- Use this module when testing Spring-based applications.


# Directory Structure
```
tzatziki-spring/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                SpringSteps.java
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                spring.feature
```

# Files

## File: tzatziki-spring/src/main/java/com/decathlon/tzatziki/steps/SpringSteps.java
```java
package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.JacksonMapper;
import com.decathlon.tzatziki.utils.Mapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.*;
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

            if (copyNamingStrategyFromSpringMapper && Objects.nonNull(objectMapper)) {
                JacksonMapper.with(mapper -> mapper.setPropertyNamingStrategy(objectMapper.getPropertyNamingStrategy()));
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
```

## File: tzatziki-spring/src/test/resources/com/decathlon/tzatziki/steps/spring.feature
```
Feature: to interact with a spring boot service

  Background:
    * we clear all the caches
    * we clear the nameOfTheCache cache

  Scenario: we can query a spring service
    When we call "/hello"
    Then we receive "Hello world!"

  Scenario: we can manage the cache
    Given the cache nameOfTheCache will contain:
      """yml
      key:
        - field_a: value_a
          field_b: value_b
      """

    Then the cache nameOfTheCache contains:
      """yml
      key:
        - field_a: value_a
      """

    Then the cache nameOfTheCache contains exactly:
      """yml
      key:
        - field_a: value_a
          field_b: value_b
      """

    And it is not true that the cache nameOfTheCache contains exactly:
      """yml
      key:
        - field_a: value_a
      """

    And it is not true that the cache nameOfTheCache contains:
      """yml
      key1:
        - field_a: value_a
      key:
        - field_a: value_b
      """

  Scenario Template: we can mock a real url
    Given that calling "http://backend/greeting" will return "Hello from another backend"
    Then calling "<endpoint>" returns "Hello from another backend"
    But if we disable the HttpInterceptor
    Then calling "<endpoint>" returns a status 500

    Examples:
      | endpoint                                 |
      | /rest-template-remote-hello              |
      | /web-client-remote-hello                 |
      | /web-client-builder-remote-hello         |
      | /web-client-from-builder-remote-hello    |

  Scenario: we can still reach the internet
    When we call "http://www.google.com"
    Then we receive a status 200
    But if calling "http://www.google.com" will return a status FORBIDDEN_403
    Then calling "http://www.google.com" returns a status FORBIDDEN_403

  Scenario: we should use Spring Context's mapper PropertyNamingStrategy by default (snake_case)
    Then it is not true that a JsonMappingException is thrown when myPojo is a NonSnakeCasePojo:
    """
    non_snake_case_field: hello
    """

  Scenario: we can get an application context bean through "_application" ObjectSteps' context variable
    Given that helloController is a HelloController "{{{[_application.getBean({{{HelloController}}})]}}}"
    And that helloResponse is "{{{[helloController.hello()]}}}"
    Then helloResponse.body is equal to "Hello world!"

  Scenario: we start an infinite task if clear thread pool executor is enabled
    Given the thread pool executor is cleaned between test runs
    And that we start an infinite task

  Scenario: then the infinite task has been cancelled
    Then infinite task has been shutdown

  Scenario: we start an infinite task if clear thread pool executor is disabled
    Given the thread pool executor is not cleaned between test runs
    And that we start an infinite task

  Scenario: then the infinite task has not been cancelled
    Then it is not true that infinite task has been shutdown
```
