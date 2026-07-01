# ADR: Extraction of `tzatziki-kafka` as a Standalone Module

**Status:** Accepted  
**Date:** 2025-03-31  
**Authors:** Copilot-assisted refactoring  
**PR:** #867  
**Modules affected:** `tzatziki-kafka` (new), `tzatziki-spring-kafka` (refactored)

---

## Summary

This ADR documents the creation of `tzatziki-kafka` as a standalone Kafka testing module not linked to Spring. The implementation extracts and centralizes the Kafka testing logic that previously lived inside `tzatziki-spring-kafka`, resulting in a clean two-module architecture based on a Backend Interface Pattern. The outcome eliminates code duplication, clearly separates Spring-only concerns, and enables Kafka BDD testing in projects that do not use the Spring framework.

---

## Context

### Problem Statement

The tzatziki project provides Cucumber BDD step definitions for testing Java applications. Before this refactoring, Kafka support was effectively provided through **`tzatziki-spring-kafka`**, whose implementation was tightly coupled to Spring (`KafkaTemplate`, `ConsumerFactory`, `KafkaInterceptor`, `EmbeddedKafkaKraftBroker`).

A new need emerged: provide a **`tzatziki-kafka` module not linked to Spring**, so Kafka steps can be used in projects relying only on the Kafka client libraries.

Without extracting the shared logic from the Spring module, satisfying that need would have required duplicating a large part of the existing implementation, including:
- ~627 lines of step definition logic
- Avro record building
- Record reading and assertion conversion
- Schema management

That approach would have created a maintenance burden where bug fixes and features would need to be implemented twice, with a high risk of behavioral divergence between the Spring and non-Spring variants.

### The Cucumber Duplicate Step Problem

Cucumber does not allow two classes on the same classpath to define step definitions with the same regex pattern. If `tzatziki-spring-kafka` depends on `tzatziki-kafka` and both contain `@Given("that a avro schema:$")`, Cucumber throws a `DuplicateStepDefinitionException`. This constraint drives the core architectural decision: **step annotations must live in exactly one place**.

---

## Decision

We adopt a **Backend Interface Pattern** with the following principles:

1. **All shared Cucumber step definitions live exclusively in `tzatziki-kafka`** (the base module)
2. A `KafkaBackend` interface abstracts operational differences between plain Kafka and Spring Kafka
3. `tzatziki-spring-kafka` depends on `tzatziki-kafka`, provides a `SpringKafkaBackend` implementation, and registers it at test startup
4. Spring-only step definitions (that have no equivalent in plain Kafka) remain in `SpringKafkaSteps`
5. Spring-only state (semaphores, wait-for-members, embedded broker lifecycle) lives in `SpringKafkaSteps`
6. Offset management is polymorphic via the backend: `disableOffsetManagement()` / `enableOffsetManagement()` work in both modes

---

## Architecture

### Module Dependency Graph

```
tzatziki-kafka (base module — no Spring dependency)
├── steps/
│   └── KafkaSteps.java              ← ALL shared Cucumber step definitions
├── kafka/
│   ├── KafkaBackend.java            ← Strategy interface
│   ├── PlainKafkaBackend.java       ← Default implementation (plain Kafka clients)
│   ├── KafkaRecordBuilder.java      ← Shared: Avro/JSON record construction
│   ├── KafkaRecordReader.java       ← Shared: record conversion for assertions
│   ├── KafkaSchemaStore.java        ← Shared: Avro schema storage/retrieval
│   ├── KafkaOffsetManager.java      ← Offset tracking (plain Kafka)
│   └── KafkaConfigurationProperties.java ← Layered configuration system

tzatziki-spring-kafka (extends base — requires Spring)
├── depends on tzatziki-kafka (excludes cucumber-picocontainer)
├── steps/
│   └── SpringKafkaSteps.java        ← Spring-only steps + state (semaphores, embedded broker)
├── kafka/
│   ├── SpringKafkaBackend.java      ← Spring implementation of KafkaBackend
│   └── KafkaInterceptor.java        ← AOP proxy for offset management + poll tracking
```

### Backend Registration Flow

**With Spring (tzatziki-spring-kafka on classpath):**
```
1. Cucumber discovers KafkaSteps + SpringKafkaSteps
2. Spring DI creates both instances
3. SpringKafkaSteps.registerBackend() → @Before(order=MIN_VALUE, runs FIRST)
   └─ KafkaSteps.setBackend(springKafkaBackend)
4. KafkaSteps.before() → @Before(order=1000, runs AFTER)
   └─ getBackend().beforeScenario() → uses SpringKafkaBackend
5. All step definitions delegate to SpringKafkaBackend
```

**Without Spring (plain Kafka only):**
```
1. Cucumber discovers KafkaSteps only
2. PicoContainer creates KafkaSteps(ObjectSteps)
3. KafkaSteps.before() → getBackend() lazily creates PlainKafkaBackend
4. All step definitions delegate to PlainKafkaBackend
```

---

## KafkaBackend Interface

```java
public interface KafkaBackend {

    // ===== Producing =====
    RecordMetadata sendAvro(ProducerRecord<String, GenericRecord> record);
    RecordMetadata sendAvroKeyMessage(ProducerRecord<GenericRecord, GenericRecord> record);
    RecordMetadata sendJson(ProducerRecord<String, String> record);
    void flushAvroProducer();
    void flushAvroKeyMessageProducer();
    void flushJsonProducer();

    // ===== Consuming (for assertions) =====
    Consumer<String, GenericRecord> getAvroConsumer(String topic);
    Consumer<String, String> getJsonConsumer(String topic);
    List<Consumer<?, ?>> getAllConsumers(String topic);

    // ===== Offset management =====
    void beforeScenario(Set<String> topicsToAutoSeek);
    long adjustedOffsetFor(TopicPartition tp);
    long consumerSeekOffset(TopicPartition tp);
    void seekConsumerToTestStart(Consumer<?, ?> consumer, String topic);
    void seekAllToEnd(String topic);
    void disableOffsetManagement();
    void enableOffsetManagement();

    // ===== Admin =====
    Map<String, Object> adminProperties();

    // ===== Lifecycle =====
    default void cleanup() {}
}
```

### Key Design: `adjustedOffsetFor` vs `consumerSeekOffset`

These two methods exist because the Spring version uses a **consumer proxy** that transparently adjusts offsets, while the plain version does not.

| Method | PlainKafkaBackend | SpringKafkaBackend | Used In |
|--------|-------------------|--------------------|---------|
| `adjustedOffsetFor(tp)` | Physical offset (from `KafkaOffsetManager`) | Physical offset (from `KafkaInterceptor`) | Admin API `alterConsumerGroupOffsets` |
| `consumerSeekOffset(tp)` | Same as above (no proxy) | `0` (proxy handles offset adjustment) | `consumer.seek()` in assertion steps |

---

## Offset Management — Two Strategies

### PlainKafkaBackend → KafkaOffsetManager (Pull-based)

Tracks offsets explicitly with a single map:
```
PAST_OFFSETS: {TopicPartition → Long}  // End of previous tests

On seekToEndAndRecord (auto-seek topics, before each scenario):
  consumer.seekToEnd(partitions) → for each tp: PAST_OFFSETS[tp] = consumer.position(tp)

On seekToTestStart:
  for each tp: consumer.seek(tp, PAST_OFFSETS[tp])
```

### SpringKafkaBackend → KafkaInterceptor (Push-based)

AOP dynamic proxy rewrites all consumer operations:
```
poll()           → updates CURRENT_OFFSETS, rewrites offsets -= PAST_OFFSETS, filters negative
seek(tp, offset) → seek(tp, offset + PAST_OFFSETS[tp])
seekToBeginning  → seek(tp, PAST_OFFSETS[tp])
position(tp)     → realPosition - PAST_OFFSETS[tp]
endOffsets(tps)  → realEndOffsets - PAST_OFFSETS
```

Each test scenario sees a virtual offset space starting at 0.

### Polymorphic Disable/Enable

The `disableOffsetManagement()` / `enableOffsetManagement()` methods on `KafkaBackend` provide a unified way to bypass offset tracking in both backends:
- **Plain Kafka:** disables `KafkaOffsetManager`
- **Spring Kafka:** disables `KafkaInterceptor` proxy offset rewriting

---

## Spring-Only Concerns

The following features are **intentionally restricted to `SpringKafkaSteps`** because they require Spring infrastructure:

### "consumed from" Step (Listener Wait)

Publishes a message then waits for a `@KafkaListener` to process it. In plain Kafka, no application listener exists, so this step would silently behave identically to `published on` — misleading users. By keeping it Spring-only, a plain Kafka user gets an "undefined step" error, guiding them to the correct approach.

### "topic was just polled" Step (Semaphore)

Creates a semaphore released by the consumer proxy when `poll()` is called. Requires the AOP proxy in `KafkaInterceptor`. Uses `tryAcquire(200ms)` to be retriable with the `within` guard.

### Embedded Broker Lifecycle

`SpringKafkaSteps.start()` / `bootstrapServers()` manage the `EmbeddedKafkaKraftBroker` lifecycle. In plain Kafka, `TestContainers` is used instead.

### Wait-for-Members / doNotWaitForMembersOn

Spring-specific consumer group coordination that has no equivalent in direct Kafka client usage.

---

## Step Definition Ownership

### `KafkaSteps` (tzatziki-kafka) — Shared Steps

| Pattern | Type | Description |
|---------|------|-------------|
| `that {guard} a avro schema:` | @Given | Define an Avro schema |
| `that the current offset of {group} on the topic {topic} is {n}` | @Given | Set consumer group offset via Admin API |
| `that we disable kafka offset manager` | @Given | Polymorphic: disable offset tracking |
| `that we enable kafka offset manager` | @Given | Polymorphic: enable offset tracking |
| `that {guard} a {record} (with key {key})? published on the {topic} topic:` | @When | Publish Avro/JSON messages |
| `that {guard} the {group} group id has fully consumed the {topic} topic` | @When | Wait for consumer group to catch up |
| `that {guard} (from the beginning )?the {topic} topic contains {comparison} a {record}:` | @Then | Assert topic content with comparison |
| `that {guard} the {topic} topic contains {n} {record}?` | @Then | Assert message count |
| `that {guard} we seek to the end of the {topic} topic` | @Given | Seek all consumers to end |

### `SpringKafkaSteps` (tzatziki-spring-kafka) — Spring-Only Steps

| Pattern | Type | Deprecated? | Description |
|---------|------|-------------|-------------|
| `that {guard} a {record} (with key)? (successfully)? consumed from the {topic} topic:` | @When | No | Publish + wait for @KafkaListener |
| `that {guard} the {topic} topic was just polled` | @When | No | Semaphore-based poll tracking |
| `that we disable kafka interceptor` | @Given | **Yes** | Use `disable kafka offset manager` |
| `that we enable kafka interceptor` | @Given | **Yes** | Use `enable kafka offset manager` |
| `that {guard} a {record} received on the {topic} topic:` | @When | **Yes** | Legacy → delegates to `consumed from` |
| `that {guard} a user receives a {name} on the topic {topic}:` | @When | **Yes** | Legacy → delegates to `consumed from` |

---

## Extracted Utility Classes

### KafkaRecordBuilder (121 lines)
All Avro and JSON record construction logic, previously duplicated inline.

### KafkaRecordReader (57 lines)
Record conversion for assertions (`ConsumerRecord` → `List<Map>`).

### KafkaSchemaStore (37 lines)
Avro schema storage/retrieval from `ObjectSteps` context.

### KafkaConfigurationProperties (229 lines)
Layered configuration system with:
- System property prefix forwarding (`tzatziki.kafka.*`)
- Programmatic customizers (global and per-topic)
- Cluster abstraction for multi-cluster topologies
- Topic-specific configuration

---

## DI Compatibility

### PicoContainer (plain Kafka)
`KafkaSteps(ObjectSteps objects)` — PicoContainer resolves `ObjectSteps` from the Cucumber glue path.

### Spring DI
`tzatziki-spring-kafka` excludes `cucumber-picocontainer` from its dependency on `tzatziki-kafka`:
```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-kafka</artifactId>
    <exclusions>
        <exclusion>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-picocontainer</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

## Backward Compatibility

### For `tzatziki-spring-kafka` users

| Before | After | Breaking? |
|--------|-------|-----------|
| `KafkaSteps.start()` | `SpringKafkaSteps.start()` | **Yes** — rename in `ApplicationContextInitializer` |
| `KafkaSteps.bootstrapServers()` | `SpringKafkaSteps.bootstrapServers()` | **Yes** — rename in initializer |
| `KafkaSteps.schemaRegistryUrl()` | `SpringKafkaSteps.schemaRegistryUrl()` | **Yes** — rename if used |
| `KafkaSteps.doNotWaitForMembersOn(...)` | `SpringKafkaSteps.doNotWaitForMembersOn(...)` | **Yes** — Spring-only API moved |
| `KafkaSteps.autoSeekTopics(...)` | `KafkaSteps.autoSeekTopics(...)` | No — stays on KafkaSteps |
| All Gherkin step patterns | Identical regex patterns | **No** — no feature file changes |
| `disable kafka interceptor` step | Still works (deprecated) | No — delegates to polymorphic method |

Breaking changes are limited to Java API calls in test configuration classes. **No Gherkin feature files require modification.**

### For new `tzatziki-kafka` users

No breaking changes — this is a new module.

---

## Code Metrics

| File | Module | Lines |
|------|--------|-------|
| KafkaSteps.java | tzatziki-kafka | 363 |
| KafkaBackend.java | tzatziki-kafka | 109 |
| PlainKafkaBackend.java | tzatziki-kafka | 257 |
| KafkaRecordBuilder.java | tzatziki-kafka | 121 |
| KafkaRecordReader.java | tzatziki-kafka | 57 |
| KafkaSchemaStore.java | tzatziki-kafka | 37 |
| KafkaOffsetManager.java | tzatziki-kafka | 117 |
| KafkaConfigurationProperties.java | tzatziki-kafka | 229 |
| SpringKafkaSteps.java | tzatziki-spring-kafka | 221 |
| SpringKafkaBackend.java | tzatziki-spring-kafka | 262 |
| KafkaInterceptor.java | tzatziki-spring-kafka | 216 |
| **Total** | | **1,989** |

- **Shared code (tzatziki-kafka):** 1,290 lines available to both modules
- **Previous duplication eliminated:** ~627 lines (entire Spring KafkaSteps duplication)

---

## Test Coverage

| Module                | Scenarios | Infrastructure                               |
|-----------------------|-----------|----------------------------------------------|
| tzatziki-kafka        | 27        | TestContainers (`apache/kafka-native:3.8.0`) |
| tzatziki-spring-kafka | 41        | `EmbeddedKafkaKraftBroker`                   |

---

## Alternatives Considered

### 1. Shared Library Module (`tzatziki-kafka-common`)
A third module with shared utilities, both modules depend on it.
**Rejected:** Still leaves duplicate step definitions. The Cucumber duplicate-step problem remains unsolved.

### 2. Step Definitions with Different Prefixes
Give Spring steps different regex patterns (e.g., `spring kafka` prefix).
**Rejected:** Breaks backward compatibility for all existing Gherkin feature files.

### 3. Abstract Base Class with Template Methods
`AbstractKafkaSteps` annotated with `@Given`/`@When`/`@Then`, subclassed.
**Rejected:** Cucumber requires annotations on concrete classes, not inherited. Also doesn't solve duplicate patterns.

### 4. Conditional Step Loading Based on Classpath
Single class detects Spring and behaves differently.
**Rejected:** Creates tight coupling, violates SRP. The Backend Interface Pattern is cleaner.

---

## Consequences

### Positive
- **Zero duplication:** Bug fixes and features are implemented once in `tzatziki-kafka`
- **Non-Spring testing:** Teams not using Spring can test Kafka with minimal dependencies
- **Clean separation:** Spring-specific behavior isolated in dedicated classes
- **Polymorphic behavior:** Operations like offset management work correctly in both modes
- **Extensibility:** New backends (Quarkus, Micronaut) can implement `KafkaBackend`
- **Unified deprecation path:** Spring-specific steps deprecated with clear migration to polymorphic alternatives

### Negative
- **One indirection layer:** Step definitions delegate through an interface
- **Two offset methods:** `adjustedOffsetFor` / `consumerSeekOffset` requires understanding the proxy behavior
- **Build dependency:** `tzatziki-spring-kafka` tests require `mvn install` of `tzatziki-kafka` first
- **Minor API break:** Spring users update 3-4 static method calls in test configuration

---

## Migration Guide for Spring Users

```java
// Before (in ApplicationContextInitializer)
KafkaSteps.start(embeddedKafka);
KafkaSteps.doNotWaitForMembersOn("my-topic");
String servers = KafkaSteps.bootstrapServers();

// After
SpringKafkaSteps.start(embeddedKafka);
SpringKafkaSteps.doNotWaitForMembersOn("my-topic");
String servers = SpringKafkaSteps.bootstrapServers();
```

Feature files remain unchanged — all step patterns are identical.
