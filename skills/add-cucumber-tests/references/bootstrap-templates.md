# Bootstrap Templates

Use these templates only when the project does not already have a working Cucumber test
bootstrap. Always check for existing infrastructure first — duplicating a runner or Spring
configuration class causes classpath conflicts.

## JUnit 5 Runner

```java
package com.example;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber.json")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.steps,com.decathlon.tzatziki.steps")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @ignore")
public class CucumberTest {}
```

Adapt to the project:

- Replace `"features"` with the actual classpath resource path (e.g., `"com/example/features"`).
- Replace `com.example.steps` with the project's step definition package.
- Use `@SelectDirectories("src/test/resources/...")` if the project already uses an absolute
  directory convention instead of classpath resources.
- For Gradle projects, change `target/cucumber.json` to `build/cucumber.json`.
- Never create a JUnit 4 runner (`@RunWith(Cucumber.class)`) unless the project explicitly
  requires an older setup.

**Important:** The `@ConfigurationParameter` values defined here are only active when Cucumber
runs through the JUnit Platform Suite engine. If you later use `-Dcucumber.features=...` to
target a specific feature, these annotations are bypassed — see `references/cli-execution.md`.

## Spring Context Configuration

When scenarios need a Spring context and no existing bootstrap exists:

```java
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@CucumberContextConfiguration
@SpringBootTest
public class CucumberSpringConfiguration {}
```

Adapt as needed:

- `@SpringBootTest(classes = MyApp.class)` — when auto-detection doesn't find the app
- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` — for HTTP tests
- `@ContextConfiguration(...)` — for non-Boot Spring projects
