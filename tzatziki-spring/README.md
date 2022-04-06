Tzatziki Spring Library
======

## Description

This module provides the base dependencies to start testing your Spring App

## Get started with this module

You need to add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-spring</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

## SpringBoot bootstrap

This module will mostly bring all the depedencies you will need to test your Spring microservice using Cucumber.

For this example you will need an empty SpringBoot project, ideally extending a version 2.5.7+.

Add this `HelloApplication` class to your project:

```java
package com.yourcompany.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HelloApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelloApplication.class, args);
    }
}
```

Then create a new file to initialize the SpringBootTest context for your application:

```java
package com.decathlon.tzatziki.steps;

import com.yourcompany.app.HelloApplication;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = HelloApplication.class)
@ContextConfiguration(initializers = HelloApplicationSteps.Initializer.class)
public class HelloApplicationSteps {

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            // here you can initialize the context with the properties needed by your application.
        }
    }
}
```

Add the following content in a file located in `src/test/resources/com/yourcompany/app/`:
```gherkin
Feature: a polite spring boot service 

  Scenario: our service can greet us
    When we call "/hello"
    Then we receive "Hello world!"
```

Right now, if you run this test by right-clicking on the `Feature` or `Scenario` line, it will fail.

But if you add this file to your project:
```java
package com.yourcompany.app.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        return ResponseEntity.ok("Hello world!");
    }
}
```

and that you run your test again, it should now pass!

## A few words about URL remapping

Since we don't really want to have to specify the port of our local spring instance, the SpringSteps will actually use the `HttpSteps.setRelativeUrlRewriter` method
so that when you call `/hello`, it actually calls `http://localhost:<spring.port>/hello`. 

This module will also intercept all the calls made by your WebClient and RestTemplate and dynamically remap the URL for the mocked ones.

This means that if in your code you have:
```java
restTemplate.getForObject(new URI("http://www.google.com"), String.class);
//or
webClient.get().uri(new URI("http://www.google.com")).retrieve().toEntity(String.class);
```

but that you have defined this mock:
```gherkin
  # we define a mock that will be remapped as http://localhost:{{mockserver.port}}/http/www.google.com
  Given that calling "http://www.google.com" will return a status FORBIDDEN_403
```

Then the url that will actually be called during your test is `http://localhost:{{mockserver.port}}/http/www.google.com`.

This behaviour can be disabled dynamically by using:
```java
HttpInterceptor.disable();
```

If you wish to intercept requests for another client than the supported ones, 
you can have a look at the `com.decathlon.tzatziki.spring.HttpInterceptor` code and write your own interceptor.

## JacksonMapper's property naming strategy override

By default, JacksonMapper will use the Spring context's ObjectMapper naming strategy.
If you want to disable it and override it with another:

Disable the Spring property naming strategy copy behaviour and override with the wanted one in your runner (HelloApplicationSteps):
```java
static {
    SpringSteps.copyNamingStrategyFromSpringMapper = false;
    JacksonMapper.with(objectMapper -> objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.WANTED_STRATEGY));
}
```

## Steps local to this library

This library doesn't come with a lot of steps, but it will start your Spring automatically 
as well as clearing all the caches between tests, if you have any.

If you want to clear the caches within a test, you can call the following step:
```gherkin
When we clear all the caches
```

or just a given cache:
```gherkin
When we clear the users cache
```

To add values in the cache:
```gherkin
Given that the cache nameOfTheCache will contain:
  """
  key: value
  """
```

And to check if the cache contains or not certain values:
```gherkin
Then the cache nameOfTheCache contains:
  """
  key: value
  """

And it is not true that the cache nameOfTheCache contains:
  """
  key: value1
  """
```
