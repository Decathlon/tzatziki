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
