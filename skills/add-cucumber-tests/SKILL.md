---
name: add-cucumber-tests
description: >
  Generates Tzatziki-based Cucumber BDD tests (.feature files) from a functional specification.
  Use this skill whenever a user wants to write Cucumber tests, add BDD scenarios, create feature files,
  generate tests, or test application behaviors with Gherkin — especially in Java/Spring projects using
  Tzatziki step definitions for HTTP, JPA, Kafka, MongoDB, OpenSearch, logging, or MCP. Also use when
  the user mentions writing integration tests, acceptance tests, or end-to-end tests in a project that
  already has Tzatziki/Cucumber dependencies, including TestNG-based setups.
---

# BDD Test Generation with Tzatziki

Generate valid Cucumber `.feature` files from a user's functional specification, using Tzatziki's
step definition library as the source of truth for legal step patterns.

## Principles

These explain the reasoning behind the workflow — understanding them helps you handle edge cases
the workflow doesn't explicitly cover.

1. **Steps come from code, not imagination.** Tzatziki provides hundreds of pre-built `@Given` /
   `@When` / `@Then` patterns in its `*Steps.java` files. Inventing step text that doesn't match
   a real definition produces `UndefinedStepException` at runtime. Per-module reference files in
   `references/steps-*.md` contain every legal step pattern — read the relevant ones before
   writing any scenario.

2. **Verify the environment before writing tests.** Run at least one existing Cucumber test in
   the target module before creating new feature files. This catches missing dependencies,
   broken bootstrap, or misconfigured runners early — before you've invested effort in writing
   scenarios that can't execute. If no test exists yet, create the minimal bootstrap first and
   confirm it discovers at least one scenario.

3. **YAML by default for structured data.** Tzatziki scenarios are most readable when request
   bodies, database fixtures, Kafka payloads, and expected results use `"""yml` doc strings.
   Fall back to Gherkin tables for naturally tabular data, or raw JSON only when the contract
   requires it.

4. **Cover exactly what the user asked for — then help them think about what they missed.**
   Generate scenarios for every functional behavior in the user's specification — not just the
   happy path. But don't silently add extra scenarios either. Instead, after covering the
   requested scope, actively identify edge cases and present them to the user as optional
   additions. To identify edge cases effectively, look at three things: (a) every external
   service call in the scenario — what happens if it returns an error (4xx, 5xx) or times out?
   (b) every data collection — what happens if it's empty or contains unexpected values?
   (c) existing test files in the project that test similar features — they often contain
   error-handling patterns you can adapt for the new scenario. The user decides which edge
   cases to include, but your job is to surface them so nothing important is missed.

5. **Reuse what exists.** If the project already has a runner, bootstrap class, feature location
   convention, or glue configuration — reuse them. Creating duplicates causes classpath conflicts
   and confuses test discovery.

## Workflow

### 1. Understand the Specification

Analyze the user's input and break it into a checklist of distinct functional behaviors. Each
behavior will become one or more scenarios. If anything is ambiguous, ask before proceeding.

While analyzing, also extract:
- **External dependencies** — every API, service, or data source the feature interacts with.
  Each one is a potential source of edge-case scenarios (errors, timeouts, empty responses).
- **Performance or reliability hints** — if the spec mentions performance concerns, large data
  volumes, or error handling, note them. These signal scenarios the user likely cares about
  even if they didn't write explicit acceptance tests for them.
- **Implicit error paths** — if the spec says "call service X to get Y", the spec is describing
  the happy path. The failure path (service X returns an error) is an edge case to suggest.

### 2. Discover the Project

Detect the build tool (prefer wrappers: `./mvnw` over `mvn`, `./gradlew` over `gradle`), then:

1. **Always read `references/steps-core.md`** — core step definitions (ObjectSteps) are used in
   every Tzatziki project for variables, assertions, and data manipulation.
2. **Detect which `tzatziki-*` modules** the project depends on by inspecting `pom.xml` or
   `build.gradle` for dependency declarations. A quick way to extract them:
   ```bash
   grep -o 'tzatziki-[a-z-]*' pom.xml | sort -u
   ```
3. **Read the matching per-module reference(s)** based on detected dependencies:
    - HTTP/REST testing → `references/steps-http.md`
    - Spring context → `references/steps-spring.md`
    - JPA/database → `references/steps-spring-jpa.md`
    - Kafka messaging → `references/steps-spring-kafka.md`
    - MongoDB → `references/steps-spring-mongodb.md`
    - OpenSearch → `references/steps-opensearch.md`
    - Logging assertions → `references/steps-logback.md`
    - MCP/AI testing → `references/steps-mcp.md`
4. When in doubt between a step pattern inferred from a `.feature` example and one read from
   the Java `*Steps.java` source, trust the Java source.
5. **Catalog error-handling patterns** from existing `.feature` files you encounter during
   discovery. Note how the project tests HTTP error responses (e.g., mocking 404/500 from
   external APIs), empty data, cache misses, or retry behavior. You'll use these patterns as
   templates when suggesting edge cases in Step 5.

### 3. Check the Bootstrap

Search for the existing test infrastructure:

- **Runner**: `@Suite` + `@IncludeEngines("cucumber")` (JUnit 5) or `AbstractTestNGCucumberTests`
- **Feature location**: `@SelectClasspathResource(...)` or `@SelectDirectories(...)`
- **Spring context**: `@CucumberContextConfiguration` + `@SpringBootTest` (if Spring is used)

If any piece is missing, plan to create it. Read `references/bootstrap-templates.md` for the
JUnit 5 runner and Spring configuration templates.

### 4. Verify the Environment

Run the existing test suite in the target module to confirm it works:

```bash
# Maven
./mvnw -pl <module> -Dtest=<RunnerClass> test
# Gradle
./gradlew :<module>:test --tests <RunnerClass>
```

A passing or functionally-failing run is fine — what matters is that Cucumber discovers and
executes scenarios. `Tests run: 0` means the runner or feature discovery is broken and needs
fixing before you write anything new.

### 5. Propose the Plan

> ⛔ **This is a mandatory checkpoint.** Do not proceed to Step 6 without the user's explicit
> approval. Even if the user's specification already includes detailed acceptance tests, present
> the plan anyway — the user expects to review it, may want to adjust scope, and needs the
> chance to approve or add edge cases they care about. Skipping this step risks generating the
> wrong tests and wasting effort.

**Output the full plan as regular response text first** (never embed a long plan inside the
`ask_user` question — the UI truncates it). Include:

- **Files to create or modify**
- **A table mapping each requested functional behavior to a scenario**
- **Any bootstrap work needed**
- **Suggested edge cases** (clearly marked as optional) — see below for how to identify them

Then use the `ask_user` tool with a **short, focused question** asking only for the user's
decision (e.g. "Does this plan look good, and which optional edge cases would you like to
include?"). Do not write any `.feature` file until the user confirms.

#### Identifying edge cases

Use the external dependencies, error patterns, and spec hints you gathered in Steps 1 and 2
to build a concrete list of optional edge-case scenarios. Work through these categories:

1. **External service failures** — For each external API call in the scenario consider what happens when it returns:
    - A client error (400, 404) — e.g., the resource doesn't exist
    - A server error (500, 502, 503) — e.g., the service is down
    - An empty or unexpected response body

2. **Empty and boundary data** — For each data collection involved:
    - What if the collection is empty? (e.g., a super model with no models, a model with no
      articles, an overridden rule with an empty `value` list)
    - What if it contains a single element vs. many?
    - What if values are null or missing?

3. **State and ordering** — Cache hit vs. miss, pre-existing vs. fresh data, concurrent
   modifications, retry after failure (CREATED → TO_RETRY → ERROR).

4. **Patterns from existing tests** — In Step 2 you cataloged error-handling patterns from the
   project's existing `.feature` files. Adapt those patterns to the new feature. For example,
   if existing tests mock a 404 from `/masterdata/v2/arbo/models/{id}/articles` and assert
   "0 kafka messages", suggest the same pattern for any new external API the feature calls.

Present each suggested edge case with a one-line description and mark them as **(optional)**.
The user decides which to include.

### 6. Implement and Validate

1. Write the `.feature` file using exact step patterns from step 2 and matching the existing
   project's style (scenario naming, `Background` usage, tags, data format conventions).
2. If no feature convention exists, default to `src/test/resources/features` with
   `@SelectClasspathResource("features")`.
3. **Run the tests and inspect the output.** The test output is the ground truth for step
   validity — no amount of visual inspection replaces it. Look specifically for these errors:
    - `"step(s) are undefined"` + `"You can implement these steps"` — means the step text
      doesn't match any registered step definition. This is the most common mistake and it
      **must** be fixed before the task is considered done. Go back to the per-module `references/steps-*.md` files,
      find the correct pattern, and rewrite the step.
    - `UndefinedStepException` — same root cause, different presentation.
    - `ParseError` — malformed Gherkin syntax.
    - `Tests run: 0` — runner or feature discovery is broken.
      Accept functional assertion failures (e.g. `expected 200 but got 404`) — they're expected
      in BDD when the product behavior isn't implemented yet.
4. Repeat until every requested behavior from step 1 is present and **zero undefined-step
   errors remain** in the test output.

**Running a single feature from the CLI:** When you need to target one specific feature file or
scenario line, use `cucumber.features` as the selector — not `-Dtest=...` or `cucumber.filter.name`.
Read `references/cli-execution.md` for the full details, because `cucumber.features` triggers
standalone Cucumber execution that bypasses the runner's `@ConfigurationParameter` annotations.

## Success Criteria

- At least one existing test was run and confirmed operational before new scenarios were written.
- **The plan was presented to the user and approved before any `.feature` file was written.**
- Generated scenarios cover 100% of the functional behaviors the user explicitly requested.
- All step text comes from real Tzatziki step definitions (no invented steps).
- **Tests were run and the output contains zero "undefined step" errors.** This is the definitive
  validation — if the test output says `"step(s) are undefined"`, the task is not done.
- Any required runner or Spring bootstrap files are in place.
- Tests are discovered and executed with the correct build tool command.
- Edge-case scenarios were identified, presented to the user, and included only if approved.
- Any remaining failures are functional assertions, not technical setup problems.

## Reference Files

Read these when needed — they contain templates and detailed guidance that would clutter
the main workflow:

### Per-Module Step References (read based on detected dependencies)

- **`references/steps-core.md`** — ObjectSteps: variables, assertions, data manipulation, type
  conversions. **Always read this** — core steps are used in every Tzatziki project.
- **`references/steps-http.md`** — HttpSteps: HTTP mocking, request/response assertions, REST API
  testing. Read when the project uses `tzatziki-http`.
- **`references/steps-spring.md`** — SpringSteps: Spring context, properties, bean manipulation.
  Read when the project uses `tzatziki-spring`.
- **`references/steps-spring-jpa.md`** — SpringJPASteps: JPA entity management, database fixtures,
  persistence assertions. Read when the project uses `tzatziki-spring-jpa`.
- **`references/steps-spring-kafka.md`** — KafkaSteps: Kafka topic management, message
  producing/consuming, async assertions. Read when the project uses `tzatziki-spring-kafka`.
- **`references/steps-spring-mongodb.md`** — SpringMongoSteps: MongoDB collection management,
  document fixtures, query asse/rtions. Read when the project uses `tzatziki-spring-mongodb`.
- **`references/steps-opensearch.md`** — OpenSearchSteps: index management, document indexing,
  search assertions. Read when the project uses `tzatziki-opensearch`.
- **`references/steps-logback.md`** — LoggerSteps: log assertion, log level management.
  Read when the project uses `tzatziki-logback`.
- **`references/steps-mcp.md`** — McpSteps: MCP server testing, tool invocation, AI integration.
  Read when the project uses `tzatziki-test-mcp`.

### Other References

- **`references/bootstrap-templates.md`** — JUnit 5 runner and Spring `@CucumberContextConfiguration`
  templates. Read when you need to create a new runner or Spring bootstrap class.
- **`references/cli-execution.md`** — How to run a single feature or scenario from the CLI using
  `cucumber.features`, including the Maven/Gradle property-mirroring mechanism. Read when you need
  to target a specific `.feature` file instead of running the full suite.
