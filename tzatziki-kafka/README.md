# tzatziki-kafka

Cucumber step definitions for testing Kafka interactions **without Spring dependency**.

This module provides the same BDD step patterns as `tzatziki-spring-kafka` but uses plain Apache Kafka Java clients (`KafkaProducer`, `KafkaConsumer`, `Admin`) instead of Spring's `KafkaTemplate`, `ConsumerFactory`, and AOP-based interceptors.

## When to use

- **Use `tzatziki-kafka`** when your project does **not** use Spring, or you want lightweight Kafka testing without a Spring context.
- **Use `tzatziki-spring-kafka`** when your project uses Spring Boot with `@KafkaListener` and needs AOP-based offset virtualization and listener interception.

## Dependencies

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-kafka</artifactId>
    <version>${tzatziki.version}</version>
    <scope>test</scope>
</dependency>
```

For embedded Kafka in tests, add TestContainers:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-kafka</artifactId>
    <version>${testcontainers.version}</version>
    <scope>test</scope>
</dependency>
```

## Configuration

### Basic Properties

Configuration is done via **system properties** (no Spring `application.properties`):

| Property | Default | Description |
|---|---|---|
| `tzatziki.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker address |
| `tzatziki.kafka.schema-registry-url` | `mock://tzatziki-kafka-steps-scope` | Schema registry URL |
| `tzatziki.kafka.consumer.group-id` | `tzatziki-kafka-test` | Default consumer group ID |
| `tzatziki.kafka.consumer.auto-offset-reset` | `earliest` | Consumer auto offset reset policy |

Set them via `-D` flags or programmatically:

```java
System.setProperty("tzatziki.kafka.bootstrap-servers", "localhost:29092");
```

### External Configuration (SSL, SASL, Schema Registry Auth, etc.)

Any Kafka client property can be configured externally using **system property prefix forwarding** and/or **programmatic customizers**.

#### System Property Prefix Forwarding

Set system properties with a scoped prefix to forward them to Kafka clients:

| Prefix | Applies to |
|---|---|
| `tzatziki.kafka.common.*` | All clients (producers, consumers, admin) |
| `tzatziki.kafka.producer.*` | All producers (avro + json) |
| `tzatziki.kafka.consumer.*` | All consumers (avro + json) |
| `tzatziki.kafka.admin.*` | Admin client only |
| `tzatziki.kafka.avro-producer.*` | Avro producers only |
| `tzatziki.kafka.json-producer.*` | JSON producers only |
| `tzatziki.kafka.avro-consumer.*` | Avro consumers only |
| `tzatziki.kafka.json-consumer.*` | JSON consumers only |

Properties are layered — later scopes override earlier ones:
`defaults → common → type-specific → format-specific`

**Example: SSL for all clients:**
```
-Dtzatziki.kafka.common.security.protocol=SSL
-Dtzatziki.kafka.common.ssl.keystore.location=/path/to/keystore.p12
-Dtzatziki.kafka.common.ssl.keystore.password=changeit
-Dtzatziki.kafka.common.ssl.truststore.location=/path/to/truststore.p12
-Dtzatziki.kafka.common.ssl.truststore.password=changeit
```

**Example: Schema registry authentication (avro only):**
```
-Dtzatziki.kafka.avro-producer.schema.registry.basic.auth.credentials.source=USER_INFO
-Dtzatziki.kafka.avro-producer.schema.registry.basic.auth.user.info=user:pass
-Dtzatziki.kafka.avro-consumer.schema.registry.basic.auth.credentials.source=USER_INFO
-Dtzatziki.kafka.avro-consumer.schema.registry.basic.auth.user.info=user:pass
```

**Example: SASL_SSL with SCRAM:**
```
-Dtzatziki.kafka.common.security.protocol=SASL_SSL
-Dtzatziki.kafka.common.sasl.mechanism=SCRAM-SHA-256
-Dtzatziki.kafka.common.sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="user" password="pass";
```

#### Programmatic Customizer

For dynamic configuration (e.g., decoding Base64 keystores from environment variables), register a customizer:

```java
import com.decathlon.tzatziki.kafka.KafkaClientType;
import com.decathlon.tzatziki.kafka.KafkaConfigurationProperties;

// In your @BeforeAll or test setup:
KafkaConfigurationProperties.customize(KafkaClientType.COMMON, props -> {
    props.put("security.protocol", "SSL");
    props.put("ssl.keystore.location", decodeKeystoreFromEnv("KEYSTORE_BASE64"));
    props.put("ssl.keystore.password", System.getenv("KEYSTORE_PASSWORD"));
});

// Scope-specific customizer (only affects avro producers):
KafkaConfigurationProperties.customize(KafkaClientType.AVRO_PRODUCER, props -> {
    props.put("schema.registry.basic.auth.credentials.source", "USER_INFO");
    props.put("schema.registry.basic.auth.user.info", System.getenv("SCHEMA_REGISTRY_AUTH"));
});
```

Customizers are applied **after** system property forwarding, giving them the highest priority.
Call `KafkaConfigurationProperties.resetCustomizers()` to clear all registered customizers.

#### Topic-Specific Configuration

Both system properties and customizers support per-topic overrides — the highest specificity level.
Topic-specific settings override global settings for that topic only.

**System property format:** `tzatziki.kafka.topic.<topic-name>.<scope>.<kafka-property>`

**Example: Different bootstrap servers per topic (multi-cluster):**
```
-Dtzatziki.kafka.common.security.protocol=SSL
-Dtzatziki.kafka.topic.orders.common.bootstrap.servers=orders-cluster:9092
-Dtzatziki.kafka.topic.events.common.bootstrap.servers=events-cluster:9092
```

**Example: Topic-specific schema registry:**
```
-Dtzatziki.kafka.topic.orders.avro-producer.schema.registry.url=http://orders-registry:8081
-Dtzatziki.kafka.topic.orders.avro-consumer.schema.registry.url=http://orders-registry:8081
```

**Programmatic per-topic customizer:**
```java
KafkaConfigurationProperties.customize("orders", KafkaClientType.PRODUCER, props -> {
    props.put("bootstrap.servers", "orders-cluster:9092");
    props.put("acks", "all");
});
```

**Full priority order (later overrides earlier):**
1. Hardcoded defaults
2. Global system properties (`tzatziki.kafka.common.*` → `producer.*` → `avro-producer.*`)
3. Topic-specific system properties (`tzatziki.kafka.topic.<topic>.common.*` → `producer.*` → `avro-producer.*`)
4. Global customizers
5. Topic-specific customizers

## Setup with TestContainers

```java
public class TestKafkaSteps {
    private static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:latest");

    @BeforeAll
    public static void beforeAll() {
        if (!kafka.isRunning()) {
            kafka.start();
        }
        System.setProperty("tzatziki.kafka.bootstrap-servers", kafka.getBootstrapServers());
        KafkaSteps.autoSeekTopics("my-output-topic"); // optional: auto-seek topics between tests
    }
}
```

## Available Steps

### Defining an Avro Schema

```gherkin
Given this avro schema:
  """yml
  type: record
  name: user
  fields:
    - name: id
      type: int
    - name: name
      type: string
  """
```

### Publishing Messages

**Avro message:**
```gherkin
When this user is published on the users topic:
  """yml
  id: 1
  name: bob
  """
```

**JSON message:**
```gherkin
When this json message is published on the users topic:
  """yml
  id: 1
  name: bob
  """
```

**With headers and key:**
```gherkin
When this json message is published on the users topic:
  """yml
  headers:
    trace-id: abc-123
  key: my-key
  value:
    id: 1
    name: bob
  """
```

**With Avro key:**
```gherkin
When this user with key userKey is published on the users topic:
  """yml
  headers:
    trace-id: key-test
  key:
    id: 42
  value:
    id: 1
    name: bob
  """
```

**Multiple messages (table):**
```gherkin
When these users are published on the users topic:
  | id | name |
  | 1  | bob  |
  | 2  | lisa |
```

### Asserting Topic Content

**Contains assertion (value only):**
```gherkin
Then the users topic contains a user:
  """yml
  id: 1
  name: bob
  """
```

**Contains assertion with headers:**
```gherkin
Then the users topic contains a json message:
  """yml
  headers:
    trace-id: abc-123
  key: my-key
  value:
    id: 1
    name: bob
  """
```

**Count assertion:**
```gherkin
Then the users topic contains 3 users
Then the empty-topic topic contains 0 users
```

**From beginning:**
```gherkin
Then from the beginning the users topic contains a user:
  """yml
  id: 1
  name: bob
  """
```

**Contains only:**
```gherkin
Then the users topic contains only a json message:
  """yml
  id: 1
  name: bob
  """
```

### Offset Management

```gherkin
Given the current offset of my-group on the topic users is 5
```

### Seeking Topics

**Seek to end** — records the offset so subsequent assertions only see messages published after the seek:
```gherkin
Given we seek to the end of the users topic
```

**Seek to beginning** — resets the consumer to the start of the topic:
```gherkin
Given we seek to the beginning of the users topic
```

**Typical use case** — ignore old messages, only assert on new ones:
```gherkin
Given we seek to the end of the events topic
When this json message is published on the events topic:
  """yml
  id: 1
  name: new-event
  """
Then the events topic contains 1 json message
```

### Template Variables

```gherkin
Given that topicName is "my-dynamic-topic"
When this json message is published on the {{topicName}} topic:
  """yml
  id: 1
  name: bob
  """
```

## Offset Isolation Between Tests

The `KafkaOffsetManager` tracks topic offsets between test scenarios. Before each scenario, it records the current end offsets as the baseline. Assertions only see messages published during the current test.

Call `KafkaSteps.autoSeekTopics("topic1", "topic2")` to automatically seek specified topics to end before each test.

## Differences from tzatziki-spring-kafka

| Feature | tzatziki-spring-kafka | tzatziki-kafka |
|---|---|---|
| Spring dependency | Required | None |
| DI mechanism | Spring + PicoContainer | PicoContainer only |
| Embedded Kafka | `EmbeddedKafkaKraftBroker` | TestContainers |
| Producers | `KafkaTemplate` beans | Plain `KafkaProducer` |
| Consumers | `ConsumerFactory` beans | Plain `KafkaConsumer` |
| Offset virtualization | AOP proxy on `ConsumerFactory` | Direct tracking in `KafkaOffsetManager` |
| Listener interception | `@Around @KafkaListener` AOP | Not available |
| "consumed from" step | Publishes + waits for app listener | Publishes + verifies on topic |
| Configuration | Spring properties | System properties |
