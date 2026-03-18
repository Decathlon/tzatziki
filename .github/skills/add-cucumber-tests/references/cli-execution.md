# Running a Single Feature from the CLI

This document explains how to target a specific `.feature` file or scenario line from the
command line using `cucumber.features`. Read this when you need to run one feature in isolation
instead of the full test suite.

## Why This Is Different from Normal Execution

Normally, Cucumber runs through the JUnit 5 Platform Suite engine. The runner class (the one
with `@Suite` + `@IncludeEngines("cucumber")`) supplies all Cucumber configuration through its
`@ConfigurationParameter` annotations — glue packages, plugins, tag filters, etc.

When you specify `-Dcucumber.features=...`, Cucumber switches to **standalone mode**. It
bypasses the Suite engine entirely and ignores all `@ConfigurationParameter` annotations. This
means the runner's glue, plugin, and tag settings vanish unless you explicitly pass them as
JVM system properties.

This is why you need to **inspect the runner first** and mirror its configuration as `-D` flags.

## Feature Selection

Use `cucumber.features` as the selector. The syntax is:

```
-Dcucumber.features=path/to/file.feature        # whole file
-Dcucumber.features=path/to/file.feature:10      # scenario at line 10
```

Do **not** use `-Dtest=RunnerClass`, `--tests RunnerClass`, or `cucumber.filter.name` as the
primary mechanism for targeting a specific feature file. Those select the runner class or filter
by scenario name — they don't replace feature file selection.

## Properties to Mirror

Inspect the runner's `@ConfigurationParameter` annotations and reproduce each one:

| Property | When to include |
|---|---|
| `cucumber.glue` | Always — without it, Cucumber won't find step definitions |
| `cucumber.plugin` | Always — otherwise no output format is configured |
| `cucumber.filter.tags` | When the runner has a tag filter (e.g., `not @ignore`) |
| `cucumber.object-factory` | When Spring or another DI framework is used |
| `cucumber.filter.name` | Only if already in the runner config as an additional filter |

## Maven

Include `-Dsurefire.includeJUnit5Engines=cucumber` so Surefire runs only the Cucumber engine
(not the Suite engine, which would try to run the full suite in parallel).

```bash
./mvnw test \
  -Dsurefire.includeJUnit5Engines=cucumber \
  -Dcucumber.features=src/test/resources/features/my-feature.feature \
  -Dcucumber.glue=com.example.steps,com.decathlon.tzatziki.steps \
  -Dcucumber.plugin=pretty,json:target/cucumber.json \
  -Dcucumber.filter.tags='not @ignore'
```

For multi-module projects, add `-pl <module>` to scope the build.

## Gradle

Gradle requires the `test` task to forward system properties. If the project's `build.gradle`
doesn't already do this, suggest the user add:

```groovy
tasks.test {
    // Forward Cucumber CLI properties
    ["cucumber.features", "cucumber.glue", "cucumber.filter.tags",
     "cucumber.filter.name", "cucumber.plugin", "cucumber.object-factory"].each { prop ->
        systemProperty(prop, System.getProperty(prop))
    }
}
```

Then run:

```bash
./gradlew test --rerun-tasks --info \
  -Dcucumber.features=src/test/resources/features/my-feature.feature \
  -Dcucumber.glue=com.example.steps,com.decathlon.tzatziki.steps \
  -Dcucumber.plugin=pretty,json:build/cucumber.json \
  -Dcucumber.filter.tags='not @ignore'
```

**Caveat:** If both the Suite Engine and the Cucumber Engine are active, this may run tests
twice. If that happens, prefer runner-based execution for validation and reserve
`cucumber.features` for focused debugging of individual scenarios.

## When to Prefer Runner-Based Execution Instead

If you can't confidently mirror all the runner's configuration — for example, because it uses
custom object factories, unusual glue arrangements, or properties you can't easily inspect —
it's safer to run the full suite through the runner:

```bash
# Maven — runs all features the runner discovers
./mvnw -pl <module> -Dtest=<RunnerClass> test

# Gradle
./gradlew :<module>:test --tests <RunnerClass>
```

This is slightly slower (it runs all features, not just one), but it guarantees the full
Cucumber configuration is active.
