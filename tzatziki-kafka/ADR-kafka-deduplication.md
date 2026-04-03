# ADR: Deduplication of Kafka Modules via Backend Interface Pattern

**Status:** Accepted  
**Date:** 2026-03-31  
**Authors:** Copilot-assisted refactoring  
**Modules affected:** `tzatziki-kafka`, `tzatziki-spring-kafka`

---

## Context

The tzatziki project provides Cucumber BDD step definitions for testing Java applications. Two modules handle Kafka testing:

1. **`tzatziki-spring-kafka`** (existing) — Kafka steps tightly coupled to Spring Framework (`KafkaTemplate`, `ConsumerFactory`, `KafkaInterceptor` AOP proxy, `EmbeddedKafkaKraftBroker`).
2. **`tzatziki-kafka`** (new) — Kafka steps using plain Apache Kafka clients (`KafkaProducer`, `KafkaConsumer`), no Spring dependency.

Before this refactoring, the new `tzatziki-kafka` module was created by duplicating ~80% of the code from `tzatziki-spring-kafka`. Both modules had:

- Identical Cucumber step definition patterns (`@Given`, `@When`, `@Then`)
- Identical Avro record building logic (`buildGenericRecordMessage`, `wrapIn`, `parseAvro`)
- Identical record reading/conversion logic (`consumerRecordsToMaps`, `asListOfRecordsWithHeaders`)
- Identical schema management logic (`getSchema`, store/retrieve from `ObjectSteps`)
- Similar but not identical offset management
- Similar but not identical producer/consumer lifecycle management

This duplication created a maintenance burden: any bug fix or feature addition to step definitions would require changes in both modules.

### The Duplicate Step Definition Problem

Cucumber does not allow two classes on the same classpath to define step definitions with the same regex pattern. If `tzatziki-spring-kafka` depends on `tzatziki-kafka`, and both contain `@Given("that a avro schema:$")`, Cucumber throws a `DuplicateStepDefinitionException` at startup. This means we **cannot** simply share code by having one module depend on the other without resolving which module owns the step annotations.

---

## Decision

We adopt a **Backend Interface Pattern** where:

1. **All Cucumber step definitions live exclusively in `tzatziki-kafka`** (the base module).
2. A `KafkaBackend` interface abstracts the operational differences between plain Kafka and Spring Kafka.
3. `tzatziki-spring-kafka` depends on `tzatziki-kafka`, provides a `SpringKafkaBackend` implementation, and registers it at test startup.
4. `tzatziki-spring-kafka` only adds step definitions for Spring-specific behaviors that have no equivalent in plain Kafka.

---

## Architecture

### Module Dependency Graph

```
tzatziki-kafka (base)
├── KafkaSteps.java          ← ALL shared step definitions (10 steps)
├── KafkaBackend.java         ← Strategy interface
├── PlainKafkaBackend.java    ← Default implementation (plain Kafka clients)
├── KafkaRecordBuilder.java   ← Shared utility: Avro/JSON record building
├── KafkaRecordReader.java    ← Shared utility: record conversion
├── KafkaSchemaStore.java     ← Shared utility: schema management
└── KafkaOffsetManager.java   ← Offset tracking (plain Kafka)

tzatziki-spring-kafka (extends)
├── depends on tzatziki-kafka (excludes cucumber-picocontainer)
├── SpringKafkaSteps.java     ← Spring-only steps (5 steps, 2 deprecated)
├── SpringKafkaBackend.java   ← Spring implementation of KafkaBackend
└── KafkaInterceptor.java     ← AOP proxy for offset management (unchanged)
```

### Backend Registration Flow

```
Test startup:
1. Cucumber discovers KafkaSteps (from tzatziki-kafka) and SpringKafkaSteps (from tzatziki-spring-kafka)
2. Spring DI creates both instances
3. SpringKafkaSteps.registerBackend() runs (@Before order=MIN_VALUE → runs FIRST)
   → KafkaSteps.setBackend(springKafkaBackend)
4. KafkaSteps.before() runs (@Before default order=1000 → runs AFTER)
   → Calls getBackend().beforeScenario() → uses SpringKafkaBackend
5. All step definitions in KafkaSteps delegate to SpringKafkaBackend
```

When `tzatziki-spring-kafka` is NOT on the classpath (plain Kafka usage):
```
1. Cucumber discovers KafkaSteps only
2. PicoContainer creates KafkaSteps(ObjectSteps objects)
3. KafkaSteps.before() runs
   → getBackend() lazily creates PlainKafkaBackend (double-checked locking)
4. All step definitions delegate to PlainKafkaBackend
```

---

## KafkaBackend Interface — Detailed Method Contracts

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
    Map<TopicPartition, Long> pastOffsets();
    void seekConsumerToTestStart(Consumer<?, ?> consumer, String topic);
    List<ConsumerRecord<?, ?>> filterForCurrentTest(ConsumerRecords<?, ?> records);

    void seekAllToEnd(String topic);
    void seekAllToBeginning(String topic);

    // ===== Admin =====
    Map<String, Object> adminProperties();
}
```

> **Note:** The `KafkaBackend` interface does **not** include "consumed from" lifecycle methods
> (`beforePublishForConsumption`, `afterPublishForConsumption`, `afterPublishForConsumptionCleanup`).
> These are Spring-only concerns, implemented directly in `SpringKafkaBackend` and called by `SpringKafkaSteps`.

### Why Two Offset Methods: `adjustedOffsetFor` vs `consumerSeekOffset`

This is the most subtle design decision. The two methods exist because the Spring version uses a **consumer proxy** that transparently adjusts offsets, while the plain version does not.

#### The Consumer Proxy Problem

In Spring, `KafkaInterceptor` creates dynamic proxies around every `Consumer` created via `ConsumerFactory`. These proxies intercept:
- `seek(tp, offset)` → adjusts to `seek(tp, offset + PAST_OFFSETS[tp])`
- `seekToBeginning(tps)` → converts to `seek(tp, PAST_OFFSETS[tp])` for each tp
- `position(tp)` → returns `realPosition - PAST_OFFSETS[tp]`
- `endOffsets(tps)` → subtracts `PAST_OFFSETS` from each value
- `poll()` → filters and rewrites record offsets

This makes each test scenario see a "virtual" offset space starting at 0, regardless of what happened in previous tests.

#### The Dual-Context Problem

The method `adjustedOffsetFor` is called in two different contexts:

1. **Admin API context** — `that_the_current_offset_the_groupid_on_topic_is` step uses the Kafka Admin API to set consumer group offsets. The Admin API operates on **physical offsets** and is NOT intercepted by the consumer proxy.

2. **Consumer seek context** — `the_topic_contains` and `the_topic_contains_n_messages` steps use `consumer.seek()` to position for reading. These consumers ARE proxied in Spring.

If we used the same value for both contexts:
- Using **physical offset** for consumer seek with Spring proxy → proxy adds offset again → **double offset** → reads wrong position
- Using **0** for admin API → admin sets offset to user value + 0 → **ignores test boundaries** → corrupts consumer group state

#### Solution

| Method | PlainKafkaBackend | SpringKafkaBackend | Used In |
|--------|-------------------|--------------------|----|
| `adjustedOffsetFor(tp)` | `KafkaOffsetManager.adjustedOffsetFor(tp)` (physical offset) | `KafkaInterceptor.adjustedOffsetFor(tp)` (physical offset) | Admin API `alterConsumerGroupOffsets` |
| `consumerSeekOffset(tp)` | `KafkaOffsetManager.adjustedOffsetFor(tp)` (same as above) | `0` (proxy handles adjustment) | `consumer.seek()` calls |

For **PlainKafkaBackend**, both methods return the same value because there is no proxy.
For **SpringKafkaBackend**, `consumerSeekOffset` returns 0 because the proxy transparently adds the physical offset.

---

## The "consumed from" Lifecycle — Spring Only

The `a_message_is_consumed_from_a_topic` step lives **exclusively in `SpringKafkaSteps`** because its semantics require Spring's AOP interceptor:

- **Plain Kafka:** No application listener exists. Publishing a message and claiming it was "consumed" would be misleading — it would silently behave identically to `published on`.
- **Spring Kafka:** Publishes a message → waits for the application's `@KafkaListener` to process it → verifies processing succeeded.

The lifecycle is implemented directly in `SpringKafkaSteps` and `SpringKafkaBackend`, not through the `KafkaBackend` interface:

```
1. SpringKafkaBackend.beforePublishForConsumption(successfully)
   └─ Sets KafkaInterceptor.awaitForSuccessfullOnly flag

2. kafkaSteps.publishMessage(name, topic, content, key)
   └─ Reuses KafkaSteps' publish logic via the public helper method

3. SpringKafkaBackend.afterPublishForConsumption(results)
   └─ Parallel-waits for each RecordMetadata to appear in PROCESSED set

4. SpringKafkaBackend.afterPublishForConsumptionCleanup()
   └─ Resets awaitForSuccessfullOnly to false
```

### How Spring Tracking Works

When `KafkaInterceptor`'s consumer proxy intercepts `poll()`, it adds each record to the `PROCESSING` set as `"topic-partition@offset"`. When the `@KafkaListener` method returns successfully, the AOP aspect moves `PROCESSING` → `PROCESSED`.

`RecordMetadata.toString()` produces the same format `"topic-partition@offset"`, so `SpringKafkaBackend.afterPublishForConsumption` can match:

```java
results.parallelStream().forEach(metadata -> {
    awaitUntil(() -> KafkaInterceptor.isProcessed(metadata.toString()));
    KafkaInterceptor.removeProcessed(metadata.toString());
});
```

The `parallelStream()` preserves the original Spring behavior where multiple messages are waited on concurrently.

---

## Extracted Utility Classes

### KafkaRecordBuilder (113 lines)

Handles all Avro and JSON record construction. Previously duplicated inline in both modules' `KafkaSteps`.

| Method | Purpose |
|--------|---------|
| `buildGenericRecordMessage(Schema, Map)` | Builds `GenericRecord` from schema + value map |
| `wrapIn(Object, Schema)` | Recursively wraps values to match Avro schema types (RECORD, ARRAY, ENUM, UNION) |
| `parseAvro(String, Schema)` | Converts string values to appropriate Avro primitive types (INT, LONG, FLOAT, DOUBLE, BOOLEAN) |
| `mapToAvroKeyMessageRecord(Schema, Schema, String, Map)` | Creates `ProducerRecord<GenericRecord, GenericRecord>` with Avro key + value + headers |
| `mapToAvroRecord(Schema, String, Map)` | Creates `ProducerRecord<String, GenericRecord>` with String key + Avro value + headers |
| `mapToJsonRecord(String, Map)` | Creates `ProducerRecord<String, String>` with JSON value + headers |

### KafkaRecordReader (54 lines)

Handles record conversion for assertions. Previously duplicated in both modules.

| Method | Purpose |
|--------|---------|
| `consumerRecordsToMaps(Iterable)` | Converts `ConsumerRecord` objects to `List<Map<String, Object>>` with `value`, `headers`, `key` keys |
| `asListOfRecordsWithHeaders(Object)` | Normalizes input content into `List<Map>` with `value` + `headers` keys. Auto-wraps bare records missing the `headers` key |

### KafkaSchemaStore (37 lines)

Handles Avro schema storage and retrieval from the `ObjectSteps` context.

| Method | Purpose |
|--------|---------|
| `storeSchema(ObjectSteps, String, Schema)` | Stores schema as `_kafka.schemas.{name}` in ObjectSteps |
| `getSchema(ObjectSteps, String)` | Retrieves schema, tries plural fallback (`name` → `name` minus last char), provides error message with documentation link |

---

## Step Definition Ownership

### In `KafkaSteps` (tzatziki-kafka) — 10 steps, shared

| Pattern | Type | Description |
|---------|------|-------------|
| `that {guard} a avro schema:` | @Given | Define an Avro schema for subsequent steps |
| `that the current offset of {group} on the topic {topic} is {n}` | @Given | Set consumer group offset via Admin API |
| `that we disable kafka offset manager` | @Given | Disable offset tracking |
| `that we enable kafka offset manager` | @Given | Enable offset tracking |
| `that {guard} a {record} (with key {key})? published on the {topic} topic:` | @When | Publish Avro/JSON messages |
| `that {guard} the {group} group id has fully consumed the {topic} topic` | @When | Wait for consumer group to catch up |
| `that {guard} (from the beginning )?the {topic} topic contains {comparison} a {record}:` | @Then | Assert topic content with comparison |
| `that {guard} the {topic} topic contains {n} {record}?` | @Then | Assert message count |
| `that {guard} we seek to the end of the {topic} topic` | @Given | Seek all consumers to the end of the topic (new messages only) |
| `that {guard} we seek to the beginning of the {topic} topic` | @Given | Seek all consumers to the beginning of the topic (all messages) |

### In `SpringKafkaSteps` (tzatziki-spring-kafka) — 6 steps, Spring-only

| Pattern | Type | Deprecated? | Description |
|---------|------|-------------|-------------|
| `that {guard} a {record} (with key {key})? (successfully )?consumed from the {topic} topic:` | @When | No | Publish + wait for `@KafkaListener` processing (requires AOP interceptor) |
| `that {guard} the {topic} topic was just polled` | @When | No | Create semaphore to track poll events (requires AOP proxy) |
| `that we disable kafka interceptor` | @Given | No | Disable the Spring AOP interceptor |
| `that we enable kafka interceptor` | @Given | No | Enable the Spring AOP interceptor |
| `that {guard} a {record} received on the {topic} topic:` | @When | **Yes** | Legacy alias → delegates to `consumed from` |
| `that {guard} a user receives a {name} on the topic {topic}:` | @When | **Yes** | Legacy alias → delegates to `consumed from` |

> **Design decision:** The `consumed from` step is **intentionally Spring-only**. In plain Kafka, there is
> no application listener to wait for, so `consumed from` would silently behave identically to `published on`,
> misleading users into thinking their message was consumed by a listener. By restricting this step to Spring,
> a plain Kafka user writing `consumed from` will get an "undefined step" error — which is the correct behavior,
> guiding them to use `published on` + `topic contains` instead.

---

## Offset Management — Two Different Strategies

### PlainKafkaBackend → KafkaOffsetManager

Uses explicit offset tracking with two maps:

```
PAST_OFFSETS:    {TopicPartition → Long}  // End of previous test
CURRENT_OFFSETS: {TopicPartition → Long}  // End of current test

Before each scenario:
  PAST_OFFSETS += CURRENT_OFFSETS
  CURRENT_OFFSETS.clear()

On seekToEndAndRecord(consumer, topic):
  consumer.seekToEnd(partitions)
  for each tp: PAST_OFFSETS[tp] = consumer.position(tp)

On filterCurrentTestRecords(records):
  return records.filter(record.offset >= PAST_OFFSETS[tp])
```

This is a **pull-based** strategy: test consumers explicitly seek and filter.

### SpringKafkaBackend → KafkaInterceptor

Uses AOP + dynamic proxy with the same two-map structure:

```
PAST_OFFSETS / CURRENT_OFFSETS: Same concept, different update mechanism

@Around @KafkaListener:
  On message processed → PROCESSING.add("topic-partition@offset")
  On success → PROCESSED.addAll(PROCESSING)

Consumer proxy rewrites:
  poll() → updates CURRENT_OFFSETS, rewrites record offsets -= PAST_OFFSETS, filters negative
  seek(tp, offset) → seek(tp, offset + PAST_OFFSETS[tp])
  seekToBeginning(tps) → seek(tp, PAST_OFFSETS[tp]) for each
  position(tp) → realPosition - PAST_OFFSETS[tp]
  endOffsets(tps) → realEndOffsets - PAST_OFFSETS for each
```

This is a **push-based** strategy: the proxy transparently rewrites all offset operations so consumers see a virtual offset space starting at 0 per test.

---

## Cross-Module References

### `KafkaSteps.semaphoreByTopic`

Declared as `public static final Map<String, Semaphore>` in `KafkaSteps` (tzatziki-kafka).

Referenced by `KafkaInterceptor` (tzatziki-spring-kafka) in the consumer proxy's `poll` handler:
```java
case "poll" -> {
    // ...
    consumer.subscription().stream()
        .filter(KafkaSteps.semaphoreByTopic::containsKey)
        .forEach(topic -> KafkaSteps.semaphoreByTopic.remove(topic).release());
    // ...
}
```

This cross-module reference works because `tzatziki-spring-kafka` depends on `tzatziki-kafka`, and `KafkaSteps` is in the shared package `com.decathlon.tzatziki.steps`.

### Schema Registry URL

- **Plain:** System property `tzatziki.kafka.schema-registry-url` (default: `http://localhost:8081`)
- **Spring:** Mock URL `"mock://tzatziki-kafka-steps-scope"` exposed via `SpringKafkaSteps.schemaRegistryUrl()`

The Spring test's `ApplicationContextInitializer` sets both the Spring property and can optionally set the system property for any code that reads it directly.

---

## DI Compatibility

### PicoContainer (plain Kafka)

`KafkaSteps(ObjectSteps objects)` — single-arg constructor, PicoContainer resolves `ObjectSteps` from the Cucumber glue path.

### Spring DI (Spring Kafka)

When `cucumber-spring` is on the classpath (via `tzatziki-spring`), all step classes are Spring-managed beans:

- `KafkaSteps(ObjectSteps objects)` — Spring injects `ObjectSteps` (created by Cucumber's Spring integration)
- `SpringKafkaSteps(KafkaSteps, ObjectSteps, KafkaTemplate, KafkaTemplate, KafkaTemplate, Optional<List>, Optional<List>, Optional<List>)` — Spring injects all beans from the application context

The `tzatziki-spring-kafka` pom.xml **excludes** `cucumber-picocontainer` from `tzatziki-kafka` to avoid DI conflicts:
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
| `KafkaSteps.autoSeekTopics(...)` | `KafkaSteps.autoSeekTopics(...)` | **No** — stays on KafkaSteps |
| `KafkaSteps.doNotWaitForMembersOn(...)` | `KafkaSteps.doNotWaitForMembersOn(...)` | **No** — stays on KafkaSteps |
| All Gherkin step patterns | All Gherkin step patterns | **No** — identical regex patterns |

The breaking changes are limited to Java API calls in test configuration classes (typically `ApplicationContextInitializer`). **No Gherkin feature file changes are required.**

### For new `tzatziki-kafka` users

No breaking changes — this is a new module. The API surface is:
- `KafkaSteps.bootstrapServers()` (reads from system property)
- `KafkaSteps.schemaRegistryUrl()` (reads from system property)
- `KafkaSteps.autoSeekTopics(...)`
- `KafkaSteps.doNotWaitForMembersOn(...)`
- All 10 Cucumber step definitions

---

## Code Metrics

### Before Refactoring

| File | Location | Lines |
|------|----------|-------|
| KafkaSteps.java | tzatziki-kafka | 614 |
| KafkaSteps.java | tzatziki-spring-kafka | 627 |
| KafkaInterceptor.java | tzatziki-spring-kafka | 201 |
| KafkaOffsetManager.java | tzatziki-kafka | 149 |
| **Total** | | **1,591** |

### After Refactoring

| File | Location | Lines |
|------|----------|-------|
| KafkaSteps.java | tzatziki-kafka | 378 |
| KafkaBackend.java | tzatziki-kafka | 81 |
| PlainKafkaBackend.java | tzatziki-kafka | 227 |
| KafkaRecordBuilder.java | tzatziki-kafka | 113 |
| KafkaRecordReader.java | tzatziki-kafka | 54 |
| KafkaSchemaStore.java | tzatziki-kafka | 37 |
| KafkaOffsetManager.java | tzatziki-kafka | 149 |
| SpringKafkaSteps.java | tzatziki-spring-kafka | 167 |
| SpringKafkaBackend.java | tzatziki-spring-kafka | 281 |
| KafkaInterceptor.java | tzatziki-spring-kafka | 209 |
| **Total** | | **1,696** |

- **Duplicated code eliminated:** ~627 lines (entire Spring KafkaSteps.java)
- **Shared code in tzatziki-kafka:** 1,039 lines available to both modules

---

## Test Results

| Module | Tests | Pass | Fail | Skip |
|--------|-------|------|------|------|
| tzatziki-kafka | 16 | 16 | 0 | 0 |
| tzatziki-spring-kafka | 52 | 51 | 0 | 1 (@ignore) |

---

## Alternatives Considered

### 1. Shared Library Module (`tzatziki-kafka-common`)

Create a third module with shared utilities, both kafka modules depend on it.

**Rejected because:** Still leaves duplicate step definitions in both modules. The Cucumber duplicate-step problem remains unsolved. Also adds a third module to maintain.

### 2. Step Definitions in Both Modules, Prefixed Differently

Give Spring steps different regex patterns (e.g., `spring kafka` prefix).

**Rejected because:** Breaks backward compatibility for all existing Gherkin feature files. Users would need to rewrite every step.

### 3. Abstract Base Class with Template Methods

`AbstractKafkaSteps` with `@Given`/`@When`/`@Then` annotations, subclassed by both modules.

**Rejected because:** Cucumber requires step definitions on concrete classes, not inherited from abstract parents (implementation limitation in cucumber-java). Also doesn't solve the duplicate-pattern problem since both subclasses would have the same patterns.

### 4. Conditional Step Loading Based on Classpath

Single KafkaSteps class that detects Spring on classpath and behaves differently.

**Rejected because:** Creates tight coupling, makes testing harder, violates single-responsibility principle. The Backend Interface Pattern achieves the same result more cleanly.

---

## Consequences

### Positive

- **Zero duplication:** Bug fixes and features are implemented once in `tzatziki-kafka`
- **Clean separation:** Spring-specific behavior is isolated in `SpringKafkaBackend` and `SpringKafkaSteps`
- **Extensibility:** New backends (e.g., for Quarkus, Micronaut) can implement `KafkaBackend` without modifying shared code
- **Testability:** Each backend can be tested independently

### Negative

- **Indirection:** Step definitions now delegate through an interface, adding one layer of abstraction
- **Two offset methods:** The `adjustedOffsetFor` / `consumerSeekOffset` split requires understanding the consumer proxy behavior
- **Build dependency:** `tzatziki-spring-kafka` tests require `mvn install` of `tzatziki-kafka` first (snapshot dependency resolution)
- **Minor API break:** Spring users must update 2-3 static method calls in their test configuration (`KafkaSteps.start()` → `SpringKafkaSteps.start()`)

---

## External Configuration Support

### Decision

Added a layered configuration system to `KafkaConfigurationProperties` combining **system property prefix forwarding** and **programmatic customizers**, allowing users to configure SSL, SASL, schema registry auth, and any other Kafka client property without modifying module code.

### Motivation

The original `PlainKafkaBackend` hardcoded all Kafka client properties (serializers, no security, no SSL). Real-world usage (e.g., connecting to secured Kafka clusters in E2E tests) requires configuring SSL keystores, SASL credentials, schema registry authentication, and other client properties.

### Mechanism

#### 1. System Property Prefix Forwarding

Any system property matching `tzatziki.kafka.<scope>.<kafka-property>` is stripped of its prefix and forwarded to the corresponding Kafka client configuration. Scopes are layered:

| Priority | Scope | Prefix | Applies to |
|----------|-------|--------|------------|
| 1 (lowest) | COMMON | `tzatziki.kafka.common.` | All clients |
| 2 | PRODUCER / CONSUMER / ADMIN | `tzatziki.kafka.producer.` etc. | Type-specific |
| 3 (highest) | AVRO_PRODUCER / JSON_CONSUMER etc. | `tzatziki.kafka.avro-producer.` etc. | Format-specific |

Within each client creation method, scopes are applied in order. For example, the avro producer uses: `COMMON → PRODUCER → AVRO_PRODUCER`.

#### 2. Programmatic Customizer

A `Consumer<Properties>` functional interface registered per `KafkaClientType` scope:

```java
KafkaConfigurationProperties.customize(KafkaClientType.COMMON, props -> {
    props.put("security.protocol", "SSL");
    props.put("ssl.keystore.location", decodeKeystoreFromEnv());
});
```

Customizers are applied **after** system properties, giving them the highest override priority.

#### `KafkaClientType` Enum

```java
public enum KafkaClientType {
    COMMON("tzatziki.kafka.common."),
    PRODUCER("tzatziki.kafka.producer."),
    CONSUMER("tzatziki.kafka.consumer."),
    ADMIN("tzatziki.kafka.admin."),
    AVRO_PRODUCER("tzatziki.kafka.avro-producer."),
    JSON_PRODUCER("tzatziki.kafka.json-producer."),
    AVRO_CONSUMER("tzatziki.kafka.avro-consumer."),
    JSON_CONSUMER("tzatziki.kafka.json-consumer.");
}
```

#### `buildProperties()` Contract

```java
// Global only (no topic-specific overrides)
public static Properties buildProperties(Properties defaults, KafkaClientType... scopes)

// With topic-specific overrides
public static Properties buildProperties(Properties defaults, String topic, KafkaClientType... scopes)
```

1. Starts with `defaults` (hardcoded serializers, bootstrap servers, etc.)
2. For each scope in order: applies global system properties (`tzatziki.kafka.<scope>.*`)
3. If topic is non-null, for each scope: applies topic-specific system properties (`tzatziki.kafka.topic.<topic>.<scope>.*`)
4. For each scope in order: applies global customizers
5. If topic is non-null, for each scope: applies topic-specific customizers
6. Returns merged `Properties`

#### Topic-Specific Configuration

Supports per-topic overrides via system properties or customizers:

**System properties:** `tzatziki.kafka.topic.<topic-name>.<scope>.<kafka-property>`
```
-Dtzatziki.kafka.topic.orders.common.bootstrap.servers=orders-cluster:9092
-Dtzatziki.kafka.topic.orders.producer.acks=all
```

**Programmatic:**
```java
KafkaConfigurationProperties.customize("orders", KafkaClientType.PRODUCER, props -> {
    props.put("bootstrap.servers", "orders-cluster:9092");
});
```

Topic-specific settings override global settings. Producers and consumers in `PlainKafkaBackend` are cached per-topic, so each topic can have its own client configuration (e.g., connecting to different Kafka clusters).

### Impact on PlainKafkaBackend

Producers are now **per-topic** (cached in maps, like consumers) to support topic-specific configuration.
Each client creation method follows this pattern:

```java
Properties defaults = new Properties();
defaults.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
defaults.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
// ... other hardcoded defaults
Properties props = KafkaConfigurationProperties.buildProperties(defaults, topic,
        KafkaClientType.COMMON, KafkaClientType.PRODUCER, KafkaClientType.AVRO_PRODUCER);
return new KafkaProducer<>(props);
```

### Backward Compatibility

Without any external configuration (no `tzatziki.kafka.common.*` system properties, no customizers), the behavior is **100% identical** to the previous hardcoded approach. The legacy accessors (`getBootstrapServers()`, `getSchemaRegistryUrl()`, etc.) are unchanged.
