# User Provided Header
Tzatziki Spring Kafka module reference (Spring-specific additions).
- SpringKafkaSteps.java defines @Given/@When/@Then patterns that only make sense with Spring Kafka: listener-wait consumption, interceptor control, embedded broker management, and deprecated steps. It builds on the core KafkaSteps from the tzatziki-kafka module (see steps-kafka.md).
- .feature files demonstrate valid Kafka step usage with YAML doc strings for message payloads.
- Prefer YAML (`"""yml`) for Kafka message bodies.


# Directory Structure
```
tzatziki-spring-kafka/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                SpringKafkaSteps.java
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                kafka.feature
```

# Files

## File: tzatziki-spring-kafka/src/main/java/com/decathlon/tzatziki/steps/SpringKafkaSteps.java
```java
package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.kafka.KafkaInterceptor;
import com.decathlon.tzatziki.kafka.SpringKafkaBackend;
import com.decathlon.tzatziki.utils.Guard;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import jakarta.annotation.Nullable;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.Assertions;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.decathlon.tzatziki.utils.Asserts.awaitUntil;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;

/**
 * Spring-specific Kafka step definitions.
 * <p>
 * Registers {@link SpringKafkaBackend} on {@link KafkaSteps} and provides
 * additional steps that only make sense with Spring Kafka (message consumption
 * with listener wait, interceptor control, poll tracking, deprecated steps).
 */
@Slf4j
@SuppressWarnings({
        "java:S100",
        "java:S107"
})
public class SpringKafkaSteps {

    public static final String SCHEMA_REGISTRY_URL = "mock://tzatziki-kafka-steps-scope";

    private static final EmbeddedKafkaBroker embeddedKafka = new EmbeddedKafkaKraftBroker(1, 1);
    private static final Set<String> checkedTopics = new LinkedHashSet<>();
    private static final Map<String, Semaphore> semaphoreByTopic = new LinkedHashMap<>();
    private static boolean isStarted;

    private final KafkaSteps kafkaSteps;
    private final ObjectSteps objects;
    private final SpringKafkaBackend springKafkaBackend;

    public SpringKafkaSteps(
            KafkaSteps kafkaSteps,
            ObjectSteps objects,
            @Nullable KafkaTemplate<GenericRecord, GenericRecord> avroKeyMessageKafkaTemplate,
            @Nullable KafkaTemplate<String, GenericRecord> avroKafkaTemplate,
            @Nullable KafkaTemplate<String, String> jsonKafkaTemplate,
            Optional<List<ConsumerFactory<Object, Object>>> avroJacksonConsumerFactories,
            Optional<List<ConsumerFactory<String, GenericRecord>>> avroConsumerFactories,
            Optional<List<ConsumerFactory<String, String>>> jsonConsumerFactories) {
        this.kafkaSteps = kafkaSteps;
        this.objects = objects;
        this.springKafkaBackend = new SpringKafkaBackend(
                avroKeyMessageKafkaTemplate,
                avroKafkaTemplate,
                jsonKafkaTemplate,
                avroJacksonConsumerFactories.orElse(new ArrayList<>()),
                avroConsumerFactories.orElse(new ArrayList<>()),
                jsonConsumerFactories.orElse(new ArrayList<>()));
    }

    // ========== Embedded Kafka lifecycle ==========

    public static synchronized void start() {
        start(null);
    }

    public static synchronized void start(Map<String, String> properties) {
        if (!isStarted) {
            isStarted = true;
            if (properties != null) {
                embeddedKafka.brokerProperties(properties);
            }
            embeddedKafka.afterPropertiesSet();
        }
    }

    public static String bootstrapServers() {
        return embeddedKafka.getBrokersAsString();
    }

    public static String schemaRegistryUrl() {
        return SCHEMA_REGISTRY_URL;
    }

    /**
     * Disables the initial consumer-group-member wait for the Spring-only
     * "consumed from" step on the given topic.
     */
    public static void doNotWaitForMembersOn(String topic) {
        checkedTopics.add(topic);
    }

    /**
     * Returns whether the Spring-only "consumed from" step already skipped or
     * completed its initial member check for the given topic.
     */
    public static boolean isTopicChecked(String topic) {
        return checkedTopics.contains(topic);
    }

    public static void registerSemaphore(String topic, Semaphore semaphore) {
        semaphoreByTopic.put(topic, semaphore);
    }

    public static boolean hasSemaphore(String topic) {
        return semaphoreByTopic.containsKey(topic);
    }

    public static Semaphore removeSemaphore(String topic) {
        return semaphoreByTopic.remove(topic);
    }

    // ========== Backend registration ==========

    @Before(order = Integer.MIN_VALUE)
    public void registerBackend() {
        KafkaSteps.setBackend(springKafkaBackend);
    }

    // ========== Spring-only steps ==========

    /**
     * Publishes a message and waits for the application's {@code @KafkaListener} to process it.
     * This step only makes sense with Spring Kafka — in plain Kafka there is no listener to wait for.
     */
    @SneakyThrows
    @When(THAT + GUARD + A + KafkaSteps.RECORD + "( with key " + VARIABLE + ")? (?:is|are)? (successfully )?consumed from the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic:$")
    public void a_message_is_consumed_from_a_topic(Guard guard, String name, String key, boolean successfully, String topicValue, Object content) {
        guard.in(objects, () -> {
            String topic = objects.resolve(topicValue);
            if (!isTopicChecked(topic)) {
                try (Admin admin = Admin.create(springKafkaBackend.adminProperties())) {
                    awaitUntil(() -> {
                        List<String> groupIds = admin.listGroups().all().get().stream().map(GroupListing::groupId).toList();
                        Map<String, KafkaFuture<ConsumerGroupDescription>> groupDescriptions = admin.describeConsumerGroups(groupIds).describedGroups();
                        return groupIds.stream()
                                .anyMatch(groupId -> unchecked(() -> groupDescriptions.get(groupId).get())
                                        .members().stream()
                                        .anyMatch(member -> member.assignment().topicPartitions().stream()
                                                .anyMatch(topicPartition -> topicPartition.topic().equals(topic))));
                    });
                    doNotWaitForMembersOn(topic);
                }
            }
            SpringKafkaBackend.beforePublishForConsumption(successfully);
            try {
                List<RecordMetadata> results = kafkaSteps.publishMessage(name, topic, content, key);
                springKafkaBackend.afterPublishForConsumption(results);
            } finally {
                SpringKafkaBackend.afterPublishForConsumptionCleanup();
            }
        });
    }

    @When(THAT + GUARD + "the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic was just polled$")
    public void topic_was_just_polled(Guard guard, String topic) {
        guard.in(objects, () -> {
            String resolvedTopic = objects.resolve(topic);
            Semaphore semaphore = new Semaphore(0);
            registerSemaphore(resolvedTopic, semaphore);
            try {
                Assertions.assertTrue(
                        semaphore.tryAcquire(200, TimeUnit.MILLISECONDS),
                        "Expected topic '%s' to be polled".formatted(resolvedTopic)
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Assertions.fail(e);
            }
        });
    }

    /**
     * @deprecated Use {@code we disable kafka offset manager} step instead.
     */
    @Deprecated(forRemoval = true)
    @Given(THAT + "we disable kafka interceptor$")
    public void disable_kafka_interceptor() {
        KafkaInterceptor.disable();
    }

    /** @deprecated Use {@code we enable kafka offset manager} step instead. */
    @Deprecated(forRemoval = true)
    @Given(THAT + "we enable kafka interceptor$")
    public void enable_kafka_interceptor() {
        KafkaInterceptor.enable();
    }

    // ========== Deprecated steps ==========

    /** @deprecated Use {@code consumed from} step instead. */
    @Deprecated(forRemoval = true)
    @When(THAT + GUARD + A + KafkaSteps.RECORD + " (?:is|are)? received on the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic:$")
    public void a_message_is_received_on_a_topic(Guard guard, String name, String topic, Object content) {
        a_message_is_consumed_from_a_topic(guard, name, null, false, objects.resolve(topic), content);
    }

    /** @deprecated Use {@code consumed from} step instead. */
    @Deprecated(forRemoval = true)
    @When(THAT + GUARD + A_USER + "receives? " + A + VARIABLE + " on the topic " + VARIABLE_OR_TEMPLATE_PATTERN + ":$")
    public void we_receive_a_message_on_a_topic(Guard guard, String name, String topic, Object content) {
        a_message_is_consumed_from_a_topic(guard, name, null, false, objects.resolve(topic), content);
    }
}
```

## File: tzatziki-spring-kafka/src/test/resources/com/decathlon/tzatziki/steps/kafka.feature
```
Feature: to interact with a spring boot service having a connection to a kafka queue

  Background:
    * a com.decathlon logger set to DEBUG
    * this avro schema:
      """yml
      type: record
      name: user
      fields:
        - name: id
          type: int
        - name: name
          type: string
      """

  Scenario: we can push an avro message in a kafka topic where a listener expect a simple payload
    When this user is consumed from the users topic:
      """yml
      id: 1
      name: bob
      """
    Then we have received 1 message on the topic users

  Scenario: we can push a list of avro messages as a table in a kafka topic where a listener expect a simple payload
    When these users are consumed from the users topic:
      | id | name |
      | 1  | bob  |
      | 2  | lisa |
    Then we have received 2 messages on the topic users

  Scenario: we can push a json message in a kafka topic where multiple listeners expect a simple payload on the same method
    When this json message is consumed from the json-users-input topic:
      """yml
      id: 1
      name: bob
      """
    Then we have received 1 message on the topic json-users-input

    When this json message is consumed from the json-users-input-2 topic:
      """yml
      id: 1
      name: jack
      """
    Then we have received 1 message on the topic json-users-input-2

  Scenario: we can push a json message with key in a kafka topic
    When this json message is consumed from the json-users-with-key topic:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key: a-key
      """
    Then we have received 1 message on the topic json-users-with-key
    And the logs contain:
      """yml
      - "?e .*received user with messageKey a-key"
      """

  Scenario: we can push a message in a kafka topic where a listener expects a list of payload, topic, partition, offset
    When these users are consumed from the users-with-headers topic:
      """yml
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 1
        name: bob
      - id: 2
        name: lisa
      """
    Then we have received 12 messages on the topic users-with-headers

  Scenario: we can push a message with a key in a kafka topic 1
    When these users are consumed from the users-with-key topic:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key: a-key
      """
    Then we have received 1 messages on the topic users-with-key
    And the logs contain:
      """yml
      - "?e .*received user with messageKey a-key on users-with-key-0@0: \\{\"id\": 1, \"name\": \"bob\"}"
      """

  Scenario: we can push a message with a key in a kafka topic 2
    Given this avro schema:
      """yml
      type: record
      name: user
      fields:
        - name: id
          type: ["null", "int"]
          default: null
        - name: name
          type: ["null", "string"]
          default: null
      """
    When these users are consumed from the users-with-key topic:
      """yml
      headers:
        uuid: some-id
      value: null
      key: a-key
      """
    Then we have received 1 messages on the topic users-with-key
    And the logs contain:
      """yml
      - "?e .*received user with messageKey a-key on users-with-key-0@0: \\{\"id\": null, \"name\": null}"
      """

  Scenario: we can push a message with an avro key in a kafka topic
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
    And this avro schema:
      """yml
      type: record
      name: user_key
      fields:
        - name: a_key
          type: string
      """
    When these users with key user_key are consumed from the users-with-avro-key topic:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key:
        a_key: a-value
      """
    Then we have received 1 messages on the topic users-with-avro-key
    And the logs contain:
      """yml
      - "?e .*received user with messageKey \\{\"a_key\": \"a-value\"} on users-with-avro-key-0@0: \\{\"id\": 1, \"name\": \"bob\"}"
      """

  Scenario: we can push a null message with an avro key in a kafka topic
    Given this avro schema:
      """yml
      type: record
      name: user
      fields:
        - name: id
          type: ["null", "int"]
          default: null
        - name: name
          type: ["null", "string"]
          default: null
      """
    And this avro schema:
      """yml
      type: record
      name: user_key
      fields:
        - name: a_key
          type: string
      """
    When these users with key user_key are consumed from the users-with-avro-key topic:
      """yml
      headers:
        uuid: some-id
      value: null
      key:
        a_key: a-value
      """
    Then we have received 1 messages on the topic users-with-avro-key
    And the logs contain:
      """yml
      - "?e .*received user with messageKey \\{\"a_key\": \"a-value\"} on users-with-avro-key-0@0: \\{\"id\": null, \"name\": null}"
      """

  Scenario Template: replaying a topic should only be replaying the messages received in this test
    When this user is consumed from the users topic:
      """yml
      id: 3
      name: tom
      """
    Then we have received 1 message on the topic users

    And if we empty the logs
    And that we replay the topic users from <from> with a <method>

    Then within 10000ms we have received 2 messages on the topic users
    But if <method> == listener => the logs contain:
      """yml
      - "?e .*received user: \\{\"id\": 3, \"name\": \"tom\"}"
      """
    But if <method> == consumer => the logs contain:
      """yml
      - "?e .*received user on users-0@0: \\{\"id\": 3, \"name\": \"tom\"}"
      """
    # these messages are from the previous test and shouldn't leak
    But it is not true that the logs contain:
      """yml
      - "?e .*received user on users-\\d@\\d+: \\{\"id\": 1, \"name\": \"bob\"}"
      - "?e .*received user on users-\\d@\\d+: \\{\"id\": 2, \"name\": \"lisa\"}"
      """

    Examples:
      | from          | method   |
      | the beginning | consumer |
      | offset 0      | consumer |
      | the beginning | listener |
      | offset 0      | listener |

  Scenario Outline: we can set the offset of a given group-id on a given topic
    When these users are consumed from the users topic:
      """yml
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 3
        name: tom
      """
    Then we have received 3 messages on the topic users

    But if the current offset of <group-id> on the topic users is 1
    And if <group-id> == users-group-id-replay => we resume replaying the topic users

    Then within 10000ms we have received 5 messages on the topic users
    Examples:
      | group-id              |
      | users-group-id        |
      | users-group-id-replay |

  Scenario: we can use an avro schema having nested records
    Given this avro schema:
      """yml
      type: record
      name: User
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: group
          type:
            name: Group
            type: record
            fields:
              - name: id
                type: int
              - name: name
                type: string
      """
    And that we receive this user on the topic users-with-group:
      """yml
      id: 1
      name: bob
      group:
        id: 1
        name: minions
      """
    Then we have received 1 message on the topic users-with-group

  Scenario: we can use an avro schema having arrays (with a default value null) of nested records set
    Given this avro schema:
      """yml
      type: record
      name: Group
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: users
          type:
            - 'null'
            - type: array
              items:
                name: User
                type: record
                fields:
                  - name: id
                    type: int
                  - name: name
                    type: string
      """
    And this Group are consumed from the group-with-users topic:
      """yml
      - id: 1
        name: minions
        users:
          - id: 1
            name: bob
      """
    Then we have received 1 message on the topic group-with-users

  Scenario: we can use an avro schema having containing arrays
    Given this avro schema:
      """yml
      type: record
      name: Group
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: users
          type:
            type: array
            items:
              name: User
              type: record
              fields:
                - name: id
                  type: int
                - name: name
                  type: string
      """
    And this Group is consumed from the group-with-users topic:
      """yml
      id: 1
      name: minions
      users:
        - id: 1
          name: bob
      """
    Then we have received 1 message on the topic group-with-users

  Scenario: we can use an avro schema having containing enum
    Given this avro schema:
      """yml
      type: record
      name: Group
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: an_enum
          type:
            type: enum
            name: AnEnum
            symbols:
              - FIRST
              - SECOND
              - THIRD
      """
    And this Group is consumed from the group-with-users topic:
      """yml
      id: 1
      name: minions
      an_enum: FIRST
      """
    Then we have received 1 message on the topic group-with-users

  Scenario Template: we can assert that a message has been sent on a topic (repeatedly)
    When this user is published on the exposed-users topic:
      """yml
      id: 1
      name: bob
      """
    Then if <consume> == true => the exposed-users topic contains only this user:
      """yml
      id: 1
      name: bob
      """
    And the exposed-users topic contains 1 message

    Examples:
      | consume |
      | false   |
      | true    |
      | false   |
      | true    |
      | true    |

  Scenario: we can set and assert the headers of a message sent to a topic
    When this user is published on the exposed-users topic:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key: a-key
      """
    Then the exposed-users topic contains this user:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key: a-key
      """
    # Still we can assert the value only
    And from the beginning the exposed-users topic contains this user:
      """yml
      id: 1
      name: bob
      """
    And the exposed-users topic contains 1 user

  Scenario: we can handle null header values in messages
    When this user is consumed from the json-users-with-key topic:
      """yml
      headers:
        uuid: some-id
        nullable-header: null
      value:
        id: 1
        name: bob
      key: a-key
      """
    Then we have received 1 message on the topic json-users-with-key

  Scenario: we can handle null header values sent on a topic
    When this json message is published on the json-users topic:
      """yml
      headers:
        uuid: some-id
        nullable-header: null
        empty-header: ""
      value:
        id: 1
        name: bob
      key: a-key
      """
    Then the json-users topic contains only this json message:
      """yml
      headers:
        uuid: some-id
        nullable-header: null
        empty-header: ""
      value:
        id: 1
        name: bob
      key: a-key
      """
    And the json-users topic contains 1 json message

  Scenario: we can assert that no message has been sent to a topic
    * the exposed-users topic contains 0 user

  Scenario: we can assert that a json message has been sent on a topic
    When this json message is published on the json-users topic:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key: a-key
      """
    Then the json-users topic contains only this json message:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key: a-key
      """
    # Still we can assert the value only
    And from the beginning the json-users topic contains only this json message:
      """yml
      id: 1
      name: bob
      """
    And the json-users topic contains 1 json message

  Scenario Template: we can assert that a json message has been sent on a topic (repeatedly)
    When this json message is published on the json-users topic:
      """yml
      id: 1
      name: bob
      """
    Then if <consume> == true => the json-users topic contains only this json message:
      """yml
      id: 1
      name: bob
      """
    And the json-users topic contains 1 json message
    Examples:
      | consume |
      | false   |
      | true    |
      | false   |
      | true    |
      | true    |

  Scenario: successive topic assertions after seeking to the end only see new messages
    When this json message is published on the json-users-successive topic:
      """yml
      id: 1
      name: before-seek
      """
    Given we seek to the end of the json-users-successive topic
    When this json message is published on the json-users-successive topic:
      """yml
      id: 2
      name: first-after-seek
      """
    Then the json-users-successive topic contains only this json message:
      """yml
      id: 2
      name: first-after-seek
      """
    When this json message is published on the json-users-successive topic:
      """yml
      id: 3
      name: second-after-seek
      """
    Then the json-users-successive topic contains only this json message:
      """yml
      id: 3
      name: second-after-seek
      """

  Scenario: we can assert that a topic will contain a message sent asynchronously
    When after 100ms this user is published on the exposed-users topic:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      """
    Then the exposed-users topic contains 1 user

  Scenario: we can use an avro schema having an array of primitives
    Given this avro schema:
      """yml
      type: record
      name: Stuff
      fields:
        - name: id
          type: int
        - name: change_set
          type:
            type: array
            items:
              type: string
              avro.java.string: String
              default: []
      """
    And that this Stuff is published on the stuffs topic:
      """yml
      id: 1
      change_set:
        - STATUS
        - ITEMS
      """
    Then the stuffs topic contains 1 stuff

  Scenario: we specify that the messages have to be successfully consumed by the listener (without throwing an expection)
    Given that the message counter will success, error then success
    And these json messages are successfully consumed from the json-users-input topic:
      | id | name    |
      | 1  | bob     |
      | 2  | patrick |
      | 3  | carlo   |
    Then we have received 3 messages on the topic json-users-input

  Scenario: successfully consumed cleanup resets interceptor state after a failure
    Given that the message counter will error then success
    And this avro schema:
      """yml
      type: record
      name: user
      fields:
        - name: id
          type: int
        - name: name
          type: string
      """
    Then it is not true that this user is successfully consumed from the users topic:
      """yml
      id: 1
      name: bob
      """
    And that the kafka interceptor success-only mode is disabled
    And within 2000ms we have received 1 message on the topic users

  Scenario: we can actively wait for a topic to be fully consumed
    Given that the message counter will success, error then success
    And these json messages are published on the json-users-input topic:
      | id | name    |
      | 1  | bob     |
      | 2  | patrick |

    When the users-group-id group id has fully consumed the json-users-input topic
    Then we have received 2 messages on the topic json-users-input

  Scenario: there shouldn't be any "within" implicit guard in Kafka assertions
    Given that this json message is published on the json-users-input topic:
      | id | name |
      | 1  | bob  |

    And that after 300ms this json message is published on the json-users-input topic:
      | id | name    |
      | 2  | patrick |

    Then the json-users-input topic contains 1 json message

    But within 500ms the json-users-input topic contains 2 json messages

  Scenario: we wait for a poll to occur on a specific topic
    When after 100ms this json message is published on the json-users-input topic:
      """yml
      id: 1
      name: poll-trigger
      """
    Then within 2000ms the json-users-input topic was just polled
    And we have received 1 message on the topic json-users-input

  Scenario: we can publish with a templated value in the topic name
    Given that topicId is "123"
    And that topicName is "template-topic-{{topicId}}"
    When this user is published on the {{topicName}} topic:
      | id | name |
      | 1  | bob  |
    Then the template-topic-123 topic contains 1 user

  Scenario: we can check with a templated value in the topic name
    Given that myTopicName is "template-topic-2"
    When this json message is published on the template-topic-2 topic:
      """yml
      headers:
        uuid: one-uuid
      value:
        id: 1
        name: bob
      key: a-key
      """
    Then the {{myTopicName}} topic contains this json message:
      """yml
      headers:
        uuid: one-uuid
      value:
        id: 1
        name: bob
      key: a-key
      """

  Scenario: we can push an avro message in a kafka template topic where a listener expect a simple payload
    Given that topicName is "users"
    When this user is consumed from the {{topicName}} topic:
      """yml
      id: 1
      name: bob
      """
    Then we have received 1 message on the topic users

  Scenario Template: we can assert that a message has been sent on a template topic (repeatedly)
    Given that topicName is "exposed-users-topic"
    When this user is published on the {{topicName}} topic:
      """yml
      id: 1
      name: bob
      """
    Then if <consume> == true => the {{topicName}} topic contains only this user:
      """yml
      id: 1
      name: bob
      """
    And the exposed-users-wrong-topic topic contains 0 user
    And the {{topicName}} topic contains 1 message

    Examples:
      | consume |
      | false   |
      | true    |
      | false   |
      | true    |
      | true    |

  Scenario Outline: we can set the offset of a given group-id on a given template topic named
    Given that topicName is "users"
    When these users are consumed from the users topic:
      """yml
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 3
        name: tom
      """
    Then we have received 3 messages on the topic users

    But if the current offset of <group-id> on the topic {{topicName}} is 1
    And if <group-id> == users-group-id-replay => we resume replaying the topic users

    Then within 10000ms we have received 5 messages on the topic users
    Examples:
      | group-id              |
      | users-group-id        |
      | users-group-id-replay |

  Scenario: error logs show expected vs actual messages when assertion fails with within guard
    Given that this json message is published on the json-users topic:
      """yml
      id: 1
      name: alice
      """
    And we empty the logs

    Then it is not true that within 500ms the json-users topic contains this json message:
      """yml
      id: 1
      name: bob
      """

    And the logs contain:
      """yml
      - ?e (?s).*Kafka assertion failed for topic 'json-users'. Expected:.*
      """

  Scenario: error logs show expected vs actual count when message count assertion fails with within guard
    Given that this json message is published on the json-users topic:
      """yml
      id: 1
      name: test
      """
    And if we empty the logs

    Then it is not true that within 500ms the json-users topic contains 2 json messages

    And the logs contain:
      """yml
      - "?e (?s).*Kafka assertion failed for topic 'json-users'. Expected 2 messages but found 1.*"
      """

  Scenario: disabling and enabling offset manager via polymorphic step
    When this json message is published on the json-offset-mgr topic:
      """yml
      id: 1
      name: first-msg
      """
    Then the json-offset-mgr topic contains 1 json message

    Given that we disable kafka offset manager
    Then from the beginning the json-offset-mgr topic contains this json message:
      """yml
      id: 1
      name: first-msg
      """
    Given that we enable kafka offset manager
    Then the json-offset-mgr topic contains 1 json message

  Scenario: deprecated disable and enable kafka interceptor steps still work
    When this json message is published on the json-interceptor-topic topic:
      """yml
      id: 1
      name: interceptor-test
      """
    Then the json-interceptor-topic topic contains 1 json message

    Given that we disable kafka interceptor
    Then from the beginning the json-interceptor-topic topic contains this json message:
      """yml
      id: 1
      name: interceptor-test
      """
    Given that we enable kafka interceptor
    Then the json-interceptor-topic topic contains 1 json message

  Scenario: deprecated received on step still works
    When this user is received on the users topic:
      """yml
      id: 1
      name: deprecated-received
      """
    Then we have received 1 message on the topic users

  Scenario: deprecated we receive on the topic step still works
    When a user receives a user on the topic users:
      """yml
      id: 1
      name: deprecated-we-receive
      """
    Then we have received 1 message on the topic users
```
