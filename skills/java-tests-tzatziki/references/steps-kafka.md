# User Provided Header
Tzatziki Kafka module reference (plain Apache Kafka, Spring-free).
- KafkaSteps.java defines @Given/@When/@Then patterns for Kafka topic management, message producing/consuming, and async assertions.
- Use this module when the project depends on `tzatziki-kafka` without Spring. For Spring Kafka specifics see steps-spring-kafka.md.
- .feature files demonstrate valid Kafka step usage with YAML doc strings for message payloads.
- Prefer YAML (`"""yml`) for Kafka message bodies.


# Directory Structure
```
tzatziki-kafka/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                KafkaSteps.java
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                kafka.feature
```

# Files

## File: tzatziki-kafka/src/main/java/com/decathlon/tzatziki/steps/KafkaSteps.java
```java
package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.kafka.*;
import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.RemoveMembersFromConsumerGroupOptions;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.UnknownMemberIdException;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.decathlon.tzatziki.utils.Asserts.awaitUntilAsserted;
import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static java.util.Locale.ROOT;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SuppressWarnings({
        "java:S100",
        "java:S3077",
        "java:S5960"
})
public class KafkaSteps {

    public static final String RECORD = "(json messages?|" + VARIABLE_PATTERN + ")";

    private static final Set<String> topicsToAutoSeek = new LinkedHashSet<>();

    private static volatile KafkaBackend backend;

    private final ObjectSteps objects;

    public KafkaSteps(ObjectSteps objects) {
        this.objects = objects;
    }

    // ========== Backend management ==========

    public static KafkaBackend getBackend() {
        if (backend == null) {
            synchronized (KafkaSteps.class) {
                if (backend == null) {
                    backend = new PlainKafkaBackend();
                }
            }
        }
        return backend;
    }

    public static void setBackend(KafkaBackend newBackend) {
        backend = newBackend;
    }

    // ========== Static configuration ==========

    public static String bootstrapServers() {
        return KafkaConfigurationProperties.getBootstrapServers();
    }

    public static String schemaRegistryUrl() {
        return KafkaConfigurationProperties.getSchemaRegistryUrl();
    }

    public static void autoSeekTopics(String... topics) {
        topicsToAutoSeek.addAll(Arrays.asList(topics));
    }

    // ========== Lifecycle ==========

    @AfterAll
    public static void afterAll() {
        if (backend != null) {
            backend.cleanup();
            backend = null;
        }
    }

    // ========== Before Hook ==========

    @Before
    public void before() {
        getBackend().beforeScenario(topicsToAutoSeek);
    }

    // ========== GIVEN Steps ==========

    @Given(THAT + GUARD + A + "avro schema:$")
    public void an_avro_schema(Guard guard, Object content) {
        guard.in(objects, () -> {
            Map<String, Object> asMap = Mapper.read(objects.resolve(content));
            String name = (String) asMap.get("name");
            assertThat(name).isNotNull();
            Schema schema = new Schema.Parser().parse(Mapper.toJson(asMap));
            KafkaSchemaStore.storeSchema(objects, name, schema);
        });
    }

    @Given(THAT + "the current offset of " + VARIABLE + " on the topic " + VARIABLE_OR_TEMPLATE_PATTERN + " is (\\d+)$")
    public void that_the_current_offset_the_groupid_on_topic_is(String groupId, String topic, long offset) throws Exception {
        try (Admin admin = Admin.create(getBackend().adminProperties())) {
            admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
            TopicPartition topicPartition = new TopicPartition(objects.resolve(topic), 0);

            int maxRetries = 5;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    Collection<MemberDescription> members = admin.describeConsumerGroups(List.of(groupId))
                            .describedGroups().get(groupId).get().members();
                    if (!members.isEmpty()) {
                        removeMembersFromConsumerGroup(groupId, admin);
                    }
                    admin.alterConsumerGroupOffsets(groupId,
                                    Map.of(topicPartition, new OffsetAndMetadata(offset + getBackend().adjustedOffsetFor(topicPartition))))
                            .partitionResult(topicPartition)
                            .get();
                    return;
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    if ((cause instanceof UnknownMemberIdException || cause instanceof GroupIdNotFoundException) && attempt < maxRetries) {
                        log.debug("{} on attempt {}, retrying...", cause.getClass().getSimpleName(), attempt);
                        Thread.sleep(200L * attempt);
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    @Given(THAT + "we disable kafka offset manager$")
    public void disable_kafka_offset_manager() {
        getBackend().disableOffsetManagement();
    }

    @Given(THAT + "we enable kafka offset manager$")
    public void enable_kafka_offset_manager() {
        getBackend().enableOffsetManagement();
    }

    @Given(THAT + GUARD + "we seek to the end of the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic$")
    public void we_seek_to_end_of_topic(Guard guard, String topicValue) {
        guard.in(objects, () -> getBackend().seekAllToEnd(objects.resolve(topicValue)));
    }

    // ========== WHEN Steps ==========

    @SneakyThrows
    @When(THAT + GUARD + A + RECORD + "( with key " + VARIABLE + ")? (?:is|are)? published on the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic:$")
    public void we_publish_on_a_topic(Guard guard, String name, String key, String topic, Object content) {
        guard.in(objects, () -> publish(name, objects.resolve(topic), content, key));
    }

    @SneakyThrows
    @When(THAT + GUARD + "the " + VARIABLE + " group id has fully consumed the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic$")
    public void topic_has_been_consumed_on_every_partition(Guard guard, String groupId, String topicValue) {
        guard.in(objects, () -> {
            String topic = objects.resolve(topicValue);
            awaitUntilAsserted(() -> getBackend().getAllConsumers(topic).forEach(consumer -> unchecked(() -> {
                try (Admin admin = Admin.create(getBackend().adminProperties())) {
                    Map<TopicPartition, OffsetAndMetadata> topicPartitionOffsetAndMetadataMap = admin
                            .listConsumerGroupOffsets(groupId)
                            .partitionsToOffsetAndMetadata().get();
                    TopicPartition tp = new TopicPartition(topic, 0);
                    if (topicPartitionOffsetAndMetadataMap.containsKey(tp)) {
                        long currentOffset = topicPartitionOffsetAndMetadataMap.get(tp).offset();
                        consumer.endOffsets(List.of(tp))
                                .forEach((topicPartition, endOffset) ->
                                        org.junit.jupiter.api.Assertions.assertEquals((long) endOffset, currentOffset));
                    } else {
                        throw new AssertionError("let's wait a bit more");
                    }
                }
            })));
        });
    }

    // ========== THEN Steps ==========

    @Then(THAT + GUARD + "(from the beginning )?the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic contains" + COMPARING_WITH + " " + A + RECORD + ":$")
    public void the_topic_contains(Guard guard, boolean fromBeginning, String topicValue, Comparison comparison, String name, String content) {
        guard.in(objects, () -> {
            String topic = objects.resolve(topicValue);
            Consumer<?, ?> consumer = getConsumer(name, topic);
            List<TopicPartition> topicPartitions = awaitTopicPartitions(topic, consumer);
            if (!consumer.assignment().containsAll(topicPartitions) || fromBeginning) {
                consumer.assign(topicPartitions);
                if (fromBeginning) {
                    for (TopicPartition tp : topicPartitions) {
                        consumer.seek(tp, getBackend().consumerSeekOffset(tp));
                    }
                } else {
                    getBackend().seekConsumerToTestStart(consumer, topic);
                }
                consumer.commitSync();
            }
            ConsumerRecords<?, ?> records = consumer.poll(Duration.ofSeconds(1));
            List<Map<String, Object>> consumerRecords = KafkaRecordReader.consumerRecordsToMaps(records);
            List<Map<?, Object>> expectedRecords = KafkaRecordReader.asListOfRecordsWithHeaders(Mapper.read(objects.resolve(content)));
            try {
                comparison.compare(consumerRecords, expectedRecords);
            } catch (AssertionError e) {
                log.error("Kafka assertion failed for topic '{}'. Expected:\n{}\nActual:\n{}",
                        topic,
                        Mapper.toYaml(expectedRecords),
                        Mapper.toYaml(consumerRecords));
                throw e;
            }
        });
    }

    @Then(THAT + GUARD + "the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic contains (\\d+) " + RECORD + "?$")
    public void the_topic_contains_n_messages(Guard guard, String topicValue, int amount, String name) {
        guard.in(objects, () -> {
            String topic = objects.resolve(topicValue);
            Consumer<?, ?> consumer = getConsumer(name, topic);
            if (amount != 0 || consumer.listTopics().containsKey(topic)) {
                List<TopicPartition> topicPartitions = awaitTopicPartitions(topic, consumer);
                if (!consumer.assignment().containsAll(topicPartitions)) {
                    consumer.assign(topicPartitions);
                    consumer.commitSync();
                }
                for (TopicPartition tp : topicPartitions) {
                    consumer.seek(tp, getBackend().consumerSeekOffset(tp));
                }
                ConsumerRecords<?, ?> records = consumer.poll(Duration.ofSeconds(1));
                try {
                    assertThat(records.count()).isEqualTo(amount);
                } catch (AssertionError e) {
                    List<Map<String, Object>> consumerRecords = KafkaRecordReader.consumerRecordsToMaps(records);
                    log.error("Kafka assertion failed for topic '{}'. Expected {} messages but found {}. Actual messages:\n{}",
                            topic, amount, records.count(), Mapper.toYaml(consumerRecords));
                    throw e;
                }
            }
        });
    }

    // ========== Publishing ==========

    /**
     * Public entry point for publishing messages, usable by extension modules (e.g. SpringKafkaSteps).
     * Resolves variables in content and delegates to the appropriate Avro/JSON publisher.
     *
     * @return metadata of all published records
     */
    public List<RecordMetadata> publishMessage(String name, String topic, Object content, String key) {
        return publish(name, topic, content, key);
    }

    private List<RecordMetadata> publish(String name, String topic, Object content, String key) {
        List<Map<?, Object>> records = KafkaRecordReader.asListOfRecordsWithHeaders(Mapper.read(objects.resolve(content)));
        log.debug("publishing {}", records);
        if (isJsonMessageType(name)) {
            return publishJson(topic, records);
        }
        return publishAvro(name, topic, records, key);
    }

    public boolean isJsonMessageType(String name) {
        return name.matches("json messages?");
    }

    @SuppressWarnings("unchecked")
    private List<RecordMetadata> publishAvro(String name, String topic, List<Map<?, Object>> records, String key) {
        Schema schema = KafkaSchemaStore.getSchema(objects, name.toLowerCase(ROOT));
        List<RecordMetadata> results;
        if (key != null) {
            Schema schemaKey = KafkaSchemaStore.getSchema(objects, key.toLowerCase(ROOT));
            results = records.stream()
                    .map(avroRecord -> {
                        ProducerRecord<org.apache.avro.generic.GenericRecord, org.apache.avro.generic.GenericRecord> producerRecord =
                                KafkaRecordBuilder.mapToAvroKeyMessageRecord(schema, schemaKey, topic, avroRecord);
                        return getBackend().sendAvroKeyMessage(producerRecord);
                    }).collect(Collectors.toList());
            getBackend().flushAvroKeyMessageProducer();
        } else {
            results = records.stream()
                    .map(avroRecord -> {
                        ProducerRecord<String, org.apache.avro.generic.GenericRecord> producerRecord =
                                KafkaRecordBuilder.mapToAvroRecord(schema, topic, (Map<String, Object>) avroRecord);
                        return getBackend().sendAvro(producerRecord);
                    }).collect(Collectors.toList());
            getBackend().flushAvroProducer();
        }
        return results;
    }

    private List<RecordMetadata> publishJson(String topic, List<Map<?, Object>> records) {
        List<RecordMetadata> results = records.stream()
                .map(jsonRecord -> getBackend().sendJson(KafkaRecordBuilder.mapToJsonRecord(topic, jsonRecord)))
                .collect(Collectors.toList());
        getBackend().flushJsonProducer();
        return results;
    }

    // ========== Consumer management ==========

    private @NotNull Consumer<?, ?> getConsumer(String name, String topic) {
        Consumer<?, ?> consumer;
        if (isJsonMessageType(name)) {
            consumer = getBackend().getJsonConsumer(topic);
        } else {
            consumer = getBackend().getAvroConsumer(topic);
        }
        assertThat(consumer).overridingErrorMessage("""
                Kafka message consumption failed. Could not create a KafkaConsumer.
                Ensure the bootstrap servers are configured via system property tzatziki.kafka.bootstrap-servers.
                """).isNotNull();
        return consumer;
    }

    // ========== Helpers ==========

    private static void removeMembersFromConsumerGroup(String groupId, Admin admin) throws InterruptedException {
        try {
            admin.removeMembersFromConsumerGroup(groupId, new RemoveMembersFromConsumerGroupOptions()).all().get();
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            log.debug("removeMembersFromConsumerGroup failed (may be expected): {}", e.getMessage());
        }
    }

    @NotNull
    private List<TopicPartition> awaitTopicPartitions(@NotNull String topic, @NotNull Consumer<?, ?> consumer) {
        List<TopicPartition> topicPartitions = new ArrayList<>();
        awaitUntilAsserted(() -> {
            List<PartitionInfo> partitions = consumer.partitionsFor(topic);
            assertThat(partitions).isNotEmpty();
            topicPartitions.addAll(partitions
                    .stream()
                    .map(partitionInfo -> new TopicPartition(partitionInfo.topic(), partitionInfo.partition()))
                    .toList());
        });
        return topicPartitions;
    }
}
```

## File: tzatziki-kafka/src/test/resources/com/decathlon/tzatziki/steps/kafka.feature
```
Feature: to interact with a kafka broker using plain kafka clients (no Spring dependency)

  Background:
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

  Scenario: we can publish and assert an avro message on a kafka topic
    When this user is published on the avro-users topic:
      """yml
      id: 1
      name: bob
      """
    Then the avro-users topic contains a user:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can publish a list of avro messages on a kafka topic
    When these users are published on the avro-users-list topic:
      | id | name |
      | 1  | bob  |
      | 2  | lisa |
    Then the avro-users-list topic contains 2 users

  Scenario: we can publish and assert a json message on a kafka topic
    When this json message is published on the json-users topic:
      """yml
      id: 1
      name: bob
      """
    Then the json-users topic contains a json message:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can publish a json message with key on a kafka topic
    When this json message is published on the json-users-with-key topic:
      """yml
      headers:
        uuid: some-id
      key: my-key
      value:
        id: 1
        name: bob
      """
    Then the json-users-with-key topic contains a json message:
      """yml
      headers:
        uuid: some-id
      key: my-key
      value:
        id: 1
        name: bob
      """

  Scenario: we can publish an avro message with headers on a kafka topic
    When this user is published on the avro-users-with-headers topic:
      """yml
      headers:
        trace-id: abc-123
      value:
        id: 1
        name: bob
      """
    Then the avro-users-with-headers topic contains a user:
      """yml
      headers:
        trace-id: abc-123
      value:
        id: 1
        name: bob
      """

  Scenario: we can publish an avro message with a null header value
    When this user is published on the avro-users-null-header topic:
      """yml
      headers:
        trace-id: null
      value:
        id: 1
        name: bob
      """
    Then the avro-users-null-header topic contains a user:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can assert a topic contains 0 messages
    Then the empty-topic topic contains 0 users

  Scenario: we can publish and assert using avro key messages
    Given this avro schema:
      """yml
      type: record
      name: userKey
      fields:
        - name: id
          type: int
      """
    When this user with key userKey is published on the avro-key-users topic:
      """yml
      headers:
        trace-id: key-test
      key:
        id: 42
      value:
        id: 1
        name: bob
      """
    Then the avro-key-users topic contains a user:
      """yml
      headers:
        trace-id: key-test
      value:
        id: 1
        name: bob
      """

  Scenario: we can publish an avro message with nested record
    Given this avro schema:
      """yml
      type: record
      name: userWithAddress
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: address
          type:
            type: record
            name: address
            fields:
              - name: street
                type: string
              - name: city
                type: string
      """
    When this userWithAddress is published on the avro-users-nested topic:
      """yml
      id: 1
      name: bob
      address:
        street: 123 Main St
        city: Springfield
      """
    Then the avro-users-nested topic contains a userWithAddress:
      """yml
      id: 1
      name: bob
      address:
        street: 123 Main St
        city: Springfield
      """

  Scenario: we can publish an avro message with an array field
    Given this avro schema:
      """yml
      type: record
      name: userWithTags
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: tags
          type:
            type: array
            items: string
      """
    When this userWithTags is published on the avro-users-array topic:
      """yml
      id: 1
      name: bob
      tags:
        - developer
        - admin
      """
    Then the avro-users-array topic contains a userWithTags:
      """yml
      id: 1
      name: bob
      tags:
        - developer
        - admin
      """

  Scenario: we can publish an avro message with an enum field
    Given this avro schema:
      """yml
      type: record
      name: userWithRole
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: role
          type:
            type: enum
            name: Role
            symbols:
              - ADMIN
              - USER
              - GUEST
      """
    When this userWithRole is published on the avro-users-enum topic:
      """yml
      id: 1
      name: bob
      role: ADMIN
      """
    Then the avro-users-enum topic contains a userWithRole:
      """yml
      id: 1
      name: bob
      role: ADMIN
      """

  Scenario: we can use template variables in topic names
    Given that topicName is "template-test-topic"
    When this json message is published on the {{topicName}} topic:
      """yml
      id: 1
      name: bob
      """
    Then the {{topicName}} topic contains a json message:
      """yml
      id: 1
      name: bob
      """

  Scenario: offset isolation between tests - first scenario publishes
    When this json message is published on the json-output topic:
      """yml
      id: 1
      name: first-test
      """
    Then the json-output topic contains a json message:
      """yml
      id: 1
      name: first-test
      """

  Scenario: offset isolation between tests - second scenario sees only its own messages
    When this json message is published on the json-output topic:
      """yml
      id: 2
      name: second-test
      """
    Then the json-output topic contains a json message:
      """yml
      id: 2
      name: second-test
      """
    And the json-output topic contains 1 json message

  Scenario: we can assert topic content with contains only comparison
    When this json message is published on the json-contains-only topic:
      """yml
      id: 1
      name: bob
      """
    Then the json-contains-only topic contains only a json message:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can publish and assert from the beginning of a topic
    When this json message is published on the json-from-beginning topic:
      """yml
      id: 1
      name: bob
      """
    Then from the beginning the json-from-beginning topic contains a json message:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can seek to the end of a topic and only see new messages
    When this json message is published on the seek-end-topic topic:
      """yml
      id: 1
      name: before-seek
      """
    Given we seek to the end of the seek-end-topic topic
    When this json message is published on the seek-end-topic topic:
      """yml
      id: 2
      name: after-seek
      """
    Then the seek-end-topic topic contains a json message:
      """yml
      id: 2
      name: after-seek
      """
    Then the seek-end-topic topic contains 1 json message

  Scenario: successive topic assertions after seeking to the end only see new messages
    When this json message is published on the seek-end-successive-topic topic:
      """yml
      id: 1
      name: before-seek
      """
    Given we seek to the end of the seek-end-successive-topic topic
    When this json message is published on the seek-end-successive-topic topic:
      """yml
      id: 2
      name: first-after-seek
      """
    Then the seek-end-successive-topic topic contains only a json message:
      """yml
      id: 2
      name: first-after-seek
      """
    When this json message is published on the seek-end-successive-topic topic:
      """yml
      id: 3
      name: second-after-seek
      """
    Then the seek-end-successive-topic topic contains only a json message:
      """yml
      id: 3
      name: second-after-seek
      """

  Scenario: we can read all messages from the beginning of a topic
    When this json message is published on the seek-begin-topic topic:
      """yml
      id: 1
      name: first
      """
    When this json message is published on the seek-begin-topic topic:
      """yml
      id: 2
      name: second
      """
    Then from the beginning the seek-begin-topic topic contains a json message:
      """yml
      - id: 1
        name: first
      - id: 2
        name: second
      """

  Scenario: disabling offset manager makes topic assertions read from beginning
    When this json message is published on the offset-mgr-topic topic:
      """yml
      id: 1
      name: first-scenario-msg
      """
    Then the offset-mgr-topic topic contains 1 json message

  Scenario: disabling and re-enabling offset manager controls offset behavior
    When this json message is published on the offset-mgr-topic topic:
      """yml
      id: 2
      name: second-scenario-msg
      """
    Given that we disable kafka offset manager
    Then from the beginning the offset-mgr-topic topic contains a json message:
      """yml
      - id: 1
        name: first-scenario-msg
      - id: 2
        name: second-scenario-msg
      """
    Given that we enable kafka offset manager
    Then the offset-mgr-topic topic contains 1 json message

  Scenario: we can publish an avro message with float and double fields
    Given this avro schema:
      """yml
      type: record
      name: measurement
      fields:
        - name: id
          type: int
        - name: temperature
          type: float
        - name: precision
          type: double
      """
    When these measurements are published on the avro-measurements topic:
      | id | temperature | precision |
      | 1  | 36.6        | 0.001     |
    Then the avro-measurements topic contains a measurement:
      """yml
      id: 1
      temperature: 36.6
      precision: 0.001
      """

  Scenario: we can publish an avro message with a boolean field
    Given this avro schema:
      """yml
      type: record
      name: flag
      fields:
        - name: id
          type: int
        - name: active
          type: boolean
      """
    When these flags are published on the avro-flags topic:
      | id | active |
      | 1  | true   |
      | 2  | false  |
    Then the avro-flags topic contains a flag:
      """yml
      - id: 1
        active: true
      - id: 2
        active: false
      """

  Scenario: we can publish an avro message with a nullable union field
    Given this avro schema:
      """yml
      type: record
      name: optionalUser
      fields:
        - name: id
          type: int
        - name: nickname
          type: ["null", "string"]
          default: null
      """
    When this optionalUser is published on the avro-optional-users topic:
      """yml
      id: 1
      nickname: bobby
      """
    Then the avro-optional-users topic contains an optionalUser:
      """yml
      id: 1
      nickname: bobby
      """

  Scenario: we can publish an avro key message with headers in plain kafka
    Given this avro schema:
      """yml
      type: record
      name: event
      fields:
        - name: id
          type: int
        - name: payload
          type: string
      """
    And this avro schema:
      """yml
      type: record
      name: eventKey
      fields:
        - name: partition_key
          type: string
      """
    When this event with key eventKey is published on the avro-key-with-headers topic:
      """yml
      headers:
        correlation-id: corr-123
        source: test-system
      key:
        partition_key: pk-001
      value:
        id: 1
        payload: hello
      """
    Then the avro-key-with-headers topic contains an event:
      """yml
      headers:
        correlation-id: corr-123
        source: test-system
      value:
        id: 1
        payload: hello
      """

  Scenario: we can set the current offset of a consumer group on a topic
    When these json messages are published on the json-group-offset-topic topic:
      | id | name   |
      | 1  | first  |
      | 2  | second |
      | 3  | third  |
    And the plain-json-group-offset group id consumes the json-group-offset-topic topic as json
    Then the plain-json-group-offset group id has consumed 3 json messages from the json-group-offset-topic topic

    Given the current offset of plain-json-group-offset on the topic json-group-offset-topic is 1
    When the plain-json-group-offset group id consumes the json-group-offset-topic topic as json

    Then the plain-json-group-offset group id has consumed 2 json messages from the json-group-offset-topic topic
    And the plain-json-group-offset group id has consumed the following json messages from the json-group-offset-topic topic:
      """yml
      - id: "2"
        name: second
      - id: "3"
        name: third
      """

  Scenario: we can assert that a consumer group has fully consumed a topic
    When these json messages are published on the json-fully-consumed-topic topic:
      | id | name   |
      | 1  | first  |
      | 2  | second |
    And the plain-json-fully-consumed group id consumes the json-fully-consumed-topic topic as json

    Then the plain-json-fully-consumed group id has fully consumed the json-fully-consumed-topic topic
    And the plain-json-fully-consumed group id has consumed 2 json messages from the json-fully-consumed-topic topic
```
