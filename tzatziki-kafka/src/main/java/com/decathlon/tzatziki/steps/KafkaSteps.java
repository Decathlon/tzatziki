package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.kafka.KafkaConfigurationProperties;
import com.decathlon.tzatziki.kafka.KafkaOffsetManager;
import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.UnknownMemberIdException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.decathlon.tzatziki.kafka.KafkaOffsetManager.offsets;
import static com.decathlon.tzatziki.utils.Asserts.awaitUntil;
import static com.decathlon.tzatziki.utils.Asserts.awaitUntilAsserted;
import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SuppressWarnings({
        "java:S100",
        "java:S5960"
})
public class KafkaSteps {

    public static final String RECORD = "(json messages?|" + VARIABLE_PATTERN + ")";

    private static final Map<String, Consumer<String, GenericRecord>> avroConsumers = new LinkedHashMap<>();
    private static final Map<String, Consumer<String, String>> jsonConsumers = new LinkedHashMap<>();
    private static final Set<String> topicsToAutoSeek = new LinkedHashSet<>();
    private static final Set<String> checkedTopics = new LinkedHashSet<>();

    private static KafkaProducer<String, GenericRecord> avroProducer;
    private static KafkaProducer<GenericRecord, GenericRecord> avroKeyMessageProducer;
    private static KafkaProducer<String, String> jsonProducer;

    private final ObjectSteps objects;

    public KafkaSteps(ObjectSteps objects) {
        this.objects = objects;
    }

    // ========== Lifecycle ==========

    public static String bootstrapServers() {
        return KafkaConfigurationProperties.getBootstrapServers();
    }

    public static String schemaRegistryUrl() {
        return KafkaConfigurationProperties.getSchemaRegistryUrl();
    }

    public static void autoSeekTopics(String... topics) {
        topicsToAutoSeek.addAll(Arrays.asList(topics));
    }

    public static void doNotWaitForMembersOn(String topic) {
        checkedTopics.add(topic);
    }

    // ========== Before Hook ==========

    @Before
    public void before() {
        KafkaOffsetManager.before();
        topicsToAutoSeek.forEach(topic -> getAllConsumers(topic).forEach(consumer ->
                KafkaOffsetManager.seekToEndAndRecord(consumer, topic)));
    }

    // ========== GIVEN Steps ==========

    @Given(THAT + GUARD + A + "avro schema:$")
    public void an_avro_schema(Guard guard, Object content) {
        guard.in(objects, () -> {
            Map<String, Object> asMap = Mapper.read(objects.resolve(content));
            String name = (String) asMap.get("name");
            assertThat(name).isNotNull();
            Schema schema = new Schema.Parser().parse(Mapper.toJson(asMap));
            objects.add("_kafka.schemas." + name.toLowerCase(ROOT), schema);
        });
    }

    @Given(THAT + "the current offset of " + VARIABLE + " on the topic " + VARIABLE_OR_TEMPLATE_PATTERN + " is (\\d+)$")
    public void that_the_current_offset_the_groupid_on_topic_is(String groupId, String topic, long offset) throws Exception {
        try (Admin admin = Admin.create(adminProperties())) {
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
                                    Map.of(topicPartition, new OffsetAndMetadata(offset + KafkaOffsetManager.adjustedOffsetFor(topicPartition))))
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

    // ========== WHEN Steps ==========

    @SneakyThrows
    @When(THAT + GUARD + A + RECORD + "( with key " + VARIABLE + ")? (?:is|are)? published on the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic:$")
    public void we_publish_on_a_topic(Guard guard, String name, String key, String topic, Object content) {
        guard.in(objects, () -> publish(name, objects.resolve(topic), content, key));
    }

    @SneakyThrows
    @When(THAT + GUARD + A + RECORD + "( with key " + VARIABLE + ")? (?:is|are)? (successfully )?consumed from the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic:$")
    public void a_message_is_consumed_from_a_topic(Guard guard, String name, String key, boolean successfully, String topicValue, Object content) {
        guard.in(objects, () -> {
            String topic = objects.resolve(topicValue);
            if (!checkedTopics.contains(topic)) {
                try (Admin admin = Admin.create(adminProperties())) {
                    awaitUntil(() -> {
                        List<String> groupIds = admin.listGroups().all().get().stream().map(GroupListing::groupId).toList();
                        if (groupIds.isEmpty()) {
                            return true;
                        }
                        Map<String, KafkaFuture<ConsumerGroupDescription>> groupDescriptions = admin.describeConsumerGroups(groupIds).describedGroups();
                        return groupIds.stream()
                                .anyMatch(groupId -> unchecked(() -> groupDescriptions.get(groupId).get())
                                        .members().stream()
                                        .anyMatch(member -> member.assignment().topicPartitions().stream()
                                                .anyMatch(topicPartition -> topicPartition.topic().equals(topic))));
                    });
                    checkedTopics.add(topic);
                }
            }
            List<RecordMetadata> results = publish(name, topic, content, key);
            log.debug("published {}", results);
        });
    }

    @SneakyThrows
    @When(THAT + GUARD + "the " + VARIABLE + " group id has fully consumed the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic$")
    public void topic_has_been_consumed_on_every_partition(Guard guard, String groupId, String topicValue) {
        guard.in(objects, () -> {
            String topic = objects.resolve(topicValue);
            awaitUntilAsserted(() -> getAllConsumers(topic).forEach(consumer -> unchecked(() -> {
                try (Admin admin = Admin.create(adminProperties())) {
                    Map<TopicPartition, OffsetAndMetadata> topicPartitionOffsetAndMetadataMap = admin
                            .listConsumerGroupOffsets(groupId)
                            .partitionsToOffsetAndMetadata().get();
                    TopicPartition tp = new TopicPartition(topic, 0);
                    if (topicPartitionOffsetAndMetadataMap.containsKey(tp)) {
                        long offset = topicPartitionOffsetAndMetadataMap.get(tp).offset();
                        consumer.endOffsets(List.of(tp))
                                .forEach((topicPartition, endOffset) ->
                                        org.junit.jupiter.api.Assertions.assertEquals((long) endOffset, offset));
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
                        consumer.seek(tp, KafkaOffsetManager.adjustedOffsetFor(tp));
                    }
                } else {
                    KafkaOffsetManager.seekToTestStart(consumer, topic);
                }
                consumer.commitSync();
            }
            try {
                ConsumerRecords<?, ?> records = consumer.poll(Duration.ofSeconds(1));
                List<ConsumerRecord<?, ?>> filtered = KafkaOffsetManager.filterCurrentTestRecords(records);
                List<Map<String, Object>> consumerRecords = consumerRecordsToMaps(filtered);
                List<Map<?, Object>> expectedRecords = asListOfRecordsWithHeaders(Mapper.read(objects.resolve(content)));
                try {
                    comparison.compare(consumerRecords, expectedRecords);
                } catch (AssertionError e) {
                    log.error("Kafka assertion failed for topic '{}'. Expected:\n{}\nActual:\n{}",
                            topic,
                            Mapper.toYaml(expectedRecords),
                            Mapper.toYaml(consumerRecords));
                    throw e;
                }
            } finally {
                TopicPartition topicPartition = new TopicPartition(topic, 0);
                ofNullable(offsets().get(topicPartition)).ifPresent(offset -> {
                    if (offset >= 0) {
                        consumer.seek(topicPartition, offset);
                    } else {
                        log.debug("offset was {} for topic {}", offset, topicPartition);
                    }
                });
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
                    consumer.seek(tp, KafkaOffsetManager.adjustedOffsetFor(tp));
                }
                ConsumerRecords<?, ?> records = consumer.poll(Duration.ofSeconds(1));
                List<ConsumerRecord<?, ?>> filtered = KafkaOffsetManager.filterCurrentTestRecords(records);
                try {
                    assertThat(filtered.size()).isEqualTo(amount);
                } catch (AssertionError e) {
                    List<Map<String, Object>> consumerRecords = consumerRecordsToMaps(filtered);
                    log.error("Kafka assertion failed for topic '{}'. Expected {} messages but found {}. Actual messages:\n{}",
                            topic, amount, filtered.size(), Mapper.toYaml(consumerRecords));
                    throw e;
                }
            }
        });
    }

    // ========== Publishing ==========

    private List<RecordMetadata> publish(String name, String topic, Object content, String key) {
        List<Map<?, Object>> records = asListOfRecordsWithHeaders(Mapper.read(objects.resolve(content)));
        log.debug("publishing {}", records);
        if (isJsonMessageType(name)) {
            return publishJson(topic, records);
        }
        return publishAvro(name, topic, records, key);
    }

    public boolean isJsonMessageType(String name) {
        return name.matches("json messages?");
    }

    @SneakyThrows
    private List<RecordMetadata> publishAvro(String name, String topic, List<Map<?, Object>> records, String key) {
        Schema schema = getSchema(name.toLowerCase(ROOT));
        List<RecordMetadata> results;
        if (key != null) {
            KafkaProducer<GenericRecord, GenericRecord> producer = getAvroKeyMessageProducer();
            results = records.stream()
                    .map(avroRecord -> {
                        Schema schemaKey = getSchema(key.toLowerCase(ROOT));
                        ProducerRecord<GenericRecord, GenericRecord> producerRecord = mapToAvroKeyMessageRecord(schema, schemaKey, topic, avroRecord);
                        return blockingSend(producer, producerRecord);
                    }).collect(Collectors.toList());
            producer.flush();
        } else {
            KafkaProducer<String, GenericRecord> producer = getAvroProducer();
            results = records.stream()
                    .map(avroRecord -> {
                        ProducerRecord<String, GenericRecord> producerRecord = mapToAvroRecord(schema, topic, (Map<String, Object>) avroRecord);
                        return blockingSend(producer, producerRecord);
                    }).collect(Collectors.toList());
            producer.flush();
        }
        return results;
    }

    @SneakyThrows
    @NotNull
    private List<RecordMetadata> publishJson(String topic, List<Map<?, Object>> records) {
        KafkaProducer<String, String> producer = getJsonProducer();
        List<RecordMetadata> results = records.stream()
                .map(jsonRecord -> blockingSend(producer, mapToJsonRecord(topic, jsonRecord)))
                .collect(Collectors.toList());
        producer.flush();
        return results;
    }

    @SneakyThrows
    private <K, V> RecordMetadata blockingSend(KafkaProducer<K, V> producer, ProducerRecord<K, V> producerRecord) {
        assertThat(producer)
                .overridingErrorMessage("Kafka producer is not initialized. Ensure bootstrapServers is configured via system property tzatziki.kafka.bootstrap-servers")
                .isNotNull();
        Future<RecordMetadata> future = producer.send(producerRecord);
        return future.get();
    }

    // ========== Record Building ==========

    @SuppressWarnings("unchecked")
    private ProducerRecord<GenericRecord, GenericRecord> mapToAvroKeyMessageRecord(Schema schemaMessage, Schema schemaKey, String topic, Map<?, Object> avroRecord) {
        GenericRecord genericRecordMessage = buildGenericRecordMessage(schemaMessage, avroRecord);

        GenericRecordBuilder genericRecordBuilderKey = new GenericRecordBuilder(schemaKey);
        Map<String, Object> keyValue = (Map<String, Object>) avroRecord.get("key");
        keyValue.forEach((fieldName, value) -> genericRecordBuilderKey.set(fieldName, wrapIn(value, schemaKey.getField(fieldName).schema())));
        GenericData.Record recordKey = genericRecordBuilderKey.build();

        ProducerRecord<GenericRecord, GenericRecord> producerRecord = new ProducerRecord<>(topic, recordKey, genericRecordMessage);
        ((Map<String, String>) avroRecord.get("headers"))
                .forEach((k, value) -> producerRecord.headers().add(k, value.getBytes(UTF_8)));

        return producerRecord;
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, GenericRecord> mapToAvroRecord(Schema schema, String topic, Map<String, Object> avroRecord) {
        GenericRecord genericRecordMessage = buildGenericRecordMessage(schema, avroRecord);

        String messageKey = (String) avroRecord.get("key");

        ProducerRecord<String, GenericRecord> producerRecord = new ProducerRecord<>(topic, messageKey, genericRecordMessage);
        ((Map<String, String>) avroRecord.get("headers"))
                .forEach((key, value) -> producerRecord.headers().add(key, value != null ? value.getBytes(UTF_8) : null));

        return producerRecord;
    }

    @SuppressWarnings("unchecked")
    private @NotNull GenericRecord buildGenericRecordMessage(Schema schemaMessage, Map<?, Object> avroRecord) {
        GenericRecordBuilder genericRecordBuilderMessage = new GenericRecordBuilder(schemaMessage);
        if (avroRecord.get("value") != null) {
            ((Map<String, Object>) avroRecord.get("value"))
                    .forEach((fieldName, value) -> genericRecordBuilderMessage.set(fieldName, wrapIn(value, schemaMessage.getField(fieldName).schema())));
        }
        return genericRecordBuilderMessage.build();
    }

    @SuppressWarnings("unchecked")
    private Object wrapIn(Object value, Schema schema) {
        if (schema.getType().equals(Schema.Type.RECORD)) {
            GenericRecordBuilder genericRecordBuilder = new GenericRecordBuilder(schema);
            ((Map<String, Object>) value).forEach((f, v) -> genericRecordBuilder.set(f, wrapIn(v, schema.getField(f).schema())));
            return genericRecordBuilder.build();
        } else if (schema.getType().equals(Schema.Type.ARRAY)) {
            Schema elementType = schema.getElementType();
            return ((List<?>) value).stream().map(element -> wrapIn(element, elementType)).collect(Collectors.toList());
        } else if (schema.getType().equals(Schema.Type.ENUM)) {
            return new GenericData.EnumSymbol(schema, value);
        } else if (schema.getType().equals(Schema.Type.UNION) && value != null) {
            Schema elementType = schema.getTypes().stream()
                    .filter(type -> type.getType() != Schema.Type.NULL)
                    .findFirst()
                    .orElseThrow();
            return wrapIn(value, elementType);
        }
        if (value instanceof String string && !schema.getType().equals(Schema.Type.STRING)) {
            value = parseAvro(string, schema);
        }
        return value;
    }

    @Nullable
    private Object parseAvro(String value, Schema valueSchema) {
        return switch (valueSchema.getType()) {
            case INT -> Integer.parseInt(value);
            case LONG -> Long.parseLong(value);
            case FLOAT -> Float.parseFloat(value);
            case DOUBLE -> Double.parseDouble(value);
            case BOOLEAN -> Boolean.parseBoolean(value);
            default -> value;
        };
    }

    @SuppressWarnings("unchecked")
    public ProducerRecord<String, String> mapToJsonRecord(String topic, Map<?, Object> jsonRecord) {
        String messageKey = (String) jsonRecord.get("key");
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, messageKey, Mapper.toJson(jsonRecord.get("value")));
        ((Map<String, String>) jsonRecord.get("headers"))
                .forEach((key, value) -> producerRecord.headers().add(key, value != null ? value.getBytes(UTF_8) : null));

        return producerRecord;
    }

    // ========== Consumer Management ==========

    private @NotNull Consumer<?, ?> getConsumer(String name, String topic) {
        Consumer<?, ?> consumer;
        if (isJsonMessageType(name)) {
            consumer = getJsonConsumer(topic);
        } else {
            consumer = getAvroConsumer(topic);
        }
        assertThat(consumer).overridingErrorMessage("""
                Kafka message consumption failed. Could not create a KafkaConsumer.
                Ensure the bootstrap servers are configured via system property tzatziki.kafka.bootstrap-servers.
                """).isNotNull();
        return consumer;
    }

    public List<Consumer<?, ?>> getAllConsumers(String topic) {
        return Stream.of(getAvroConsumer(topic), getJsonConsumer(topic))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Consumer<String, GenericRecord> getAvroConsumer(String topic) {
        return avroConsumers.computeIfAbsent(topic, t -> {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID() + "_avro_" + t);
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, KafkaConfigurationProperties.getConsumerAutoOffsetReset());
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroDeserializer.class.getName());
            props.put("schema.registry.url", schemaRegistryUrl());
            return new KafkaConsumer<>(props);
        });
    }

    public Consumer<String, String> getJsonConsumer(String topic) {
        return jsonConsumers.computeIfAbsent(topic, t -> {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID() + "_json_" + t);
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, KafkaConfigurationProperties.getConsumerAutoOffsetReset());
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            return new KafkaConsumer<>(props);
        });
    }

    // ========== Producer Management ==========

    private synchronized KafkaProducer<String, GenericRecord> getAvroProducer() {
        if (avroProducer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", schemaRegistryUrl());
            avroProducer = new KafkaProducer<>(props);
        }
        return avroProducer;
    }

    private synchronized KafkaProducer<GenericRecord, GenericRecord> getAvroKeyMessageProducer() {
        if (avroKeyMessageProducer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", schemaRegistryUrl());
            avroKeyMessageProducer = new KafkaProducer<>(props);
        }
        return avroKeyMessageProducer;
    }

    private synchronized KafkaProducer<String, String> getJsonProducer() {
        if (jsonProducer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            jsonProducer = new KafkaProducer<>(props);
        }
        return jsonProducer;
    }

    // ========== Helpers ==========

    private Map<String, Object> adminProperties() {
        return Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
    }

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

    private List<Map<String, Object>> consumerRecordsToMaps(Iterable<?> records) {
        return StreamSupport.stream(records.spliterator(), false)
                .map(r -> {
                    ConsumerRecord<?, ?> record = (ConsumerRecord<?, ?>) r;
                    Map<String, String> headers = Stream.of(record.headers().toArray())
                            .collect(HashMap::new,
                                    (map, header) -> map.put(header.key(), header.value() != null ? new String(header.value()) : null),
                                    HashMap::putAll);
                    Map<String, Object> value = Mapper.read(record.value().toString());
                    String messageKey = record.key() != null ? String.valueOf(record.key()) : "";
                    return Map.of("value", value, "headers", (Object) headers, "key", (Object) messageKey);
                })
                .map(m -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("value", m.get("value"));
                    result.put("headers", m.get("headers"));
                    result.put("key", m.get("key"));
                    return result;
                })
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public List<Map<?, Object>> asListOfRecordsWithHeaders(Object content) {
        List<Map<Object, Object>> records = content instanceof Map
                ? List.of((Map<Object, Object>) content)
                : (List<Map<Object, Object>>) content;
        return records.stream()
                .map(record -> {
                    final int recordSize = record.size();
                    if (2 <= recordSize && recordSize <= 3 && record.containsKey("value") && record.containsKey("headers")) {
                        return record;
                    }
                    return Map.<String, Object>of("value", record, "headers", new LinkedHashMap<>());
                }).collect(Collectors.toList());
    }

    public Schema getSchema(String name) {
        Object schema = objects.getOrSelf("_kafka.schemas." + name);
        if (schema instanceof Schema avroSchema) {
            return avroSchema;
        }
        schema = objects.getOrSelf("_kafka.schemas." + name.substring(0, name.length() - 1));
        assertThat(schema)
                .overridingErrorMessage(
                        "The Avro schema for '" + name + "' was not found. You can follow the steps below to solve the issue:\n" +
                                "- ensure that the schema .avsc file has been correctly added using the 'avro schema' step. Doc: https://github.com/Decathlon/tzatziki/tree/main/tzatziki-kafka#defining-an-avro-schema\n" +
                                "- confirm that the object '" + name + "' in your step matches the value of the 'name' property defined in the Avro schema.\n")
                .isInstanceOf(Schema.class);
        return (Schema) schema;
    }
}
