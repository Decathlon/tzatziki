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
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaFuture;
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
        KafkaOffsetManager.disable();
    }

    @Given(THAT + "we enable kafka offset manager$")
    public void enable_kafka_offset_manager() {
        KafkaOffsetManager.enable();
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
