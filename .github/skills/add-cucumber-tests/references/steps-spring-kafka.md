# User Provided Header
Tzatziki Spring Kafka module reference.
- KafkaSteps.java defines @Given/@When/@Then patterns for Kafka topic management, message producing/consuming, and async assertions.
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

## File: tzatziki-spring-kafka/src/main/java/com/decathlon/tzatziki/steps/KafkaSteps.java
```java
package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.kafka.KafkaInterceptor;
import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Mapper;
import com.decathlon.tzatziki.utils.Methods;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.annotation.Nullable;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
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
import org.apache.kafka.common.header.Header;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.decathlon.tzatziki.kafka.KafkaInterceptor.offsets;
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
        "java:S100", // Allow method names with underscores for BDD steps
        "java:S5960" // Address Sonar warning: False positive assertion check on non-production code.
})
public class KafkaSteps {

    public static final String RECORD = "(json messages?|" + VARIABLE_PATTERN + ")";
    private static final EmbeddedKafkaBroker embeddedKafka = new EmbeddedKafkaKraftBroker(1, 1);

    private static final Map<String, List<Consumer<Object, Object>>> avroJacksonConsumers = new LinkedHashMap<>();
    private static final Map<String, Consumer<?, GenericRecord>> avroConsumers = new LinkedHashMap<>();
    private static final Map<String, Consumer<String, String>> jsonConsumers = new LinkedHashMap<>();
    private static final Set<String> topicsToAutoSeek = new LinkedHashSet<>();
    private static final Set<String> checkedTopics = new LinkedHashSet<>();

    private static boolean isStarted;

    public static final Map<String, Semaphore> semaphoreByTopic = new LinkedHashMap<>();

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

    public static void doNotWaitForMembersOn(String topic) {
        checkedTopics.add(topic);
    }

    public static String bootstrapServers() {
        return embeddedKafka.getBrokersAsString();
    }

    private final ObjectSteps objects;

    private final KafkaTemplate<GenericRecord, GenericRecord> avroKeyMessageKafkaTemplate;

    private final KafkaTemplate<String, GenericRecord> avroKafkaTemplate;

    private final KafkaTemplate<String, String> jsonKafkaTemplate;

    List<ConsumerFactory<Object, Object>> avroJacksonConsumerFactories;

    List<ConsumerFactory<String, GenericRecord>> avroConsumerFactories;

    List<ConsumerFactory<String, String>> jsonConsumerFactories;

    public KafkaSteps(ObjectSteps objects, @Nullable KafkaTemplate<GenericRecord, GenericRecord> avroKeyMessageKafkaTemplate, @Nullable KafkaTemplate<String, GenericRecord> avroKafkaTemplate, @Nullable KafkaTemplate<String, String> jsonKafkaTemplate, Optional<List<ConsumerFactory<Object, Object>>> avroJacksonConsumerFactories, Optional<List<ConsumerFactory<String, GenericRecord>>> avroConsumerFactories, Optional<List<ConsumerFactory<String, String>>> jsonConsumerFactories) {
        this.objects = objects;
        this.avroKeyMessageKafkaTemplate = avroKeyMessageKafkaTemplate;
        this.avroKafkaTemplate = avroKafkaTemplate;
        this.jsonKafkaTemplate = jsonKafkaTemplate;
        this.avroJacksonConsumerFactories = avroJacksonConsumerFactories.orElse(new ArrayList<>());
        this.avroConsumerFactories = avroConsumerFactories.orElse(new ArrayList<>());
        this.jsonConsumerFactories = jsonConsumerFactories.orElse(new ArrayList<>());
    }

    public static String schemaRegistryUrl() {
        return "mock://tzatziki-kafka-steps-scope";
    }

    public static void autoSeekTopics(String... topics) {
        topicsToAutoSeek.addAll(Arrays.asList(topics));
    }

    @Before
    public void before() {
        KafkaInterceptor.before();
        topicsToAutoSeek.forEach(topic -> this.getAllConsumers(topic).forEach(consumer -> {
            Map<String, List<PartitionInfo>> partitionsByTopic = consumer.listTopics();
            if (partitionsByTopic.containsKey(topic)) {
                List<TopicPartition> topicPartitions = partitionsByTopic.get(topic).stream()
                        .map(partitionInfo -> new TopicPartition(partitionInfo.topic(), partitionInfo.partition())).collect(Collectors.toList());
                if (!consumer.assignment().containsAll(topicPartitions)) {
                    consumer.assign(topicPartitions);
                    consumer.commitSync();
                }
                consumer.seekToEnd(topicPartitions);
                KafkaInterceptor.disable();
                consumer.partitionsFor(topic).stream()
                        .map(partitionInfo -> new TopicPartition(topic, partitionInfo.partition()))
                        .forEach(topicPartition -> {
                            long position = consumer.position(topicPartition);
                            log.debug("setting offset of %s topic to %s".formatted(topicPartition.topic(), position));
                            offsets().put(topicPartition, position);
                        });
                KafkaInterceptor.enable();
            }
        }));
    }

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

    @SneakyThrows
    @When(THAT + GUARD + A + RECORD + " (?:is|are)? published on the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic:$")
    public void we_publish_on_a_topic(Guard guard, String name, String topic, Object content) {
        guard.in(objects, () -> publish(name, objects.resolve(topic), content, null));
    }

    @Deprecated(forRemoval = true)
    @When(THAT + GUARD + A + RECORD + " (?:is|are)? received on the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic:$")
    public void a_message_is_received_on_a_topic(Guard guard, String name, String topic, Object content) {
        a_message_is_consumed_from_a_topic(guard, name, null, false, objects.resolve(topic), content);
    }

    @Deprecated(forRemoval = true)
    @When(THAT + GUARD + A_USER + "receives? " + A + VARIABLE + " on the topic " + VARIABLE_OR_TEMPLATE_PATTERN + ":$")
    public void we_receive_a_message_on_a_topic(Guard guard, String name, String topic, Object content) {
        a_message_is_consumed_from_a_topic(guard, name, null, false, objects.resolve(topic), content);
    }


    @SneakyThrows
    @When(THAT + GUARD + A + RECORD + "( with key " + VARIABLE + ")? (?:is|are)? (successfully )?consumed from the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic:$")
    public void a_message_is_consumed_from_a_topic(Guard guard, String name, String key, boolean successfully, String topicValue, Object content) {
        guard.in(objects, () -> {
            KafkaInterceptor.awaitForSuccessfullOnly = successfully;
            String topic = objects.resolve(topicValue);
            if (!checkedTopics.contains(topic)) {
                try (Admin admin = Admin.create(getAnyConsumerFactory().getConfigurationProperties())) {
                    awaitUntil(() -> {
                        List<String> groupIds = admin.listGroups().all().get().stream().map(GroupListing::groupId).toList();
                        Map<String, KafkaFuture<ConsumerGroupDescription>> groupDescriptions = admin.describeConsumerGroups(groupIds).describedGroups();
                        return groupIds.stream()
                                .anyMatch(groupId -> unchecked(() -> groupDescriptions.get(groupId).get())
                                        .members().stream()
                                        .anyMatch(member -> member
                                                .assignment()
                                                .topicPartitions()
                                                .stream()
                                                .anyMatch(topicPartition -> {
                                                    log.debug("groupid %s is listening on topic %s".formatted(groupId, topic));
                                                    return topicPartition.topic().equals(topic);
                                                }))
                                );
                    });
                    checkedTopics.add(topic);
                }
            }
            List<RecordMetadata> results = publish(name, topic, content, key)
                    .parallelStream()
                    .map(KafkaInterceptor::waitUntilProcessed)
                    .map(SendResult::getRecordMetadata)
                    .collect(Collectors.toList());
            log.debug("processed {}", results);
            KafkaInterceptor.awaitForSuccessfullOnly = false;
        });
    }

    @NotNull
    private ConsumerFactory<?, ?> getAnyConsumerFactory() {
        return Stream.concat(Stream.concat(jsonConsumerFactories.stream(), avroConsumerFactories.stream()), avroJacksonConsumerFactories.stream()).findFirst().get();
    }

    @SneakyThrows
    @When(THAT + GUARD + "the " + VARIABLE + " group id has fully consumed the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic$")
    public void topic_has_been_consumed_on_every_partition(Guard guard, String groupId, String topicValue) {
        guard.in(objects, () -> {
            String topic = objects.resolve(topicValue);
            awaitUntilAsserted(() -> getAllConsumers(topic).forEach(consumer -> unchecked(() -> {
                try (Admin admin = Admin.create(avroConsumerFactories.get(0).getConfigurationProperties())) {
                    Map<TopicPartition, OffsetAndMetadata> topicPartitionOffsetAndMetadataMap = admin
                            .listConsumerGroupOffsets(groupId)
                            .partitionsToOffsetAndMetadata().get();
                    TopicPartition key = new TopicPartition(topic, 0);
                    if (topicPartitionOffsetAndMetadataMap.containsKey(key)) {
                        long offset = topicPartitionOffsetAndMetadataMap.get(key).offset();
                        consumer.endOffsets(List.of(key))
                                .forEach((topicPartition, endOffset) -> Assertions.assertEquals((long) endOffset, offset));
                    } else {
                        throw new AssertionError("let's wait a bit more");
                    }
                }
            })));
        });
    }

    @When(THAT + GUARD + "the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic was just polled$")
    public void topic_was_just_polled(Guard guard, String topic) {
        guard.in(objects, () -> {
            Semaphore semaphore = new Semaphore(0);
            semaphoreByTopic.put(objects.resolve(topic), semaphore);
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Assertions.fail(e);
            }
        });
    }

    @Given(THAT + "the current offset of " + VARIABLE + " on the topic " + VARIABLE_OR_TEMPLATE_PATTERN + " is (\\d+)$")
    public void that_the_current_offset_the_groupid_on_topic_is(String groupId, String topic, long offset) throws ExecutionException, InterruptedException {
        try (Admin admin = Admin.create(avroConsumerFactories.get(0).getConfigurationProperties())) {
            admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
            TopicPartition topicPartition = new TopicPartition(objects.resolve(topic), 0);
            
            // Retry logic to handle race conditions with consumer group membership
            int maxRetries = 5;
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    Collection<MemberDescription> members = admin.describeConsumerGroups(List.of(groupId)).describedGroups().get(groupId).get().members();
                    if (!members.isEmpty()) {
                        removeMembersFromConsumerGroup(groupId, admin);
                    }
                    admin.alterConsumerGroupOffsets(groupId, Map.of(topicPartition, new OffsetAndMetadata(offset + KafkaInterceptor.adjustedOffsetFor(topicPartition))))
                            .partitionResult(topicPartition)
                            .get();
                    // Success - exit the retry loop
                    return;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    // UnknownMemberIdException may occur if a consumer begin tested is removed from the group while still running. 
                    // GroupIdNotFoundException can happen when the consumer group has not yet been created or initialized.
                    if ((cause instanceof UnknownMemberIdException || cause instanceof GroupIdNotFoundException) && attempt < maxRetries) {
                        log.debug("{} on attempt {}, retrying...", cause.getClass().getSimpleName(), attempt);
                        Thread.sleep(200 * attempt); // Exponential backoff
                    } else {
                        throw e;
                    }
                }
            }
        }
    }

    private static void removeMembersFromConsumerGroup(String groupId, Admin admin) throws InterruptedException {
        try {
            admin.removeMembersFromConsumerGroup(groupId, new RemoveMembersFromConsumerGroupOptions()).all().get();
            // Wait briefly for the coordinator to stabilize after member removal
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // Restore interrupted state...
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            // this could happen if the consumer group emptied itself meanwhile
            log.debug("removeMembersFromConsumerGroup failed (may be expected): {}", e.getMessage());
        }
    }

    @Then(THAT + GUARD + "(from the beginning )?the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic contains" + COMPARING_WITH + " " + A + RECORD + ":$")
    public void the_topic_contains(Guard guard, boolean fromBeginning, String topicValue, Comparison comparison, String name, String content) {
        guard.in(objects, () ->
        {
            String topic = objects.resolve(topicValue);
            Consumer<?, ?> consumer = getConsumer(name, topic);
            List<TopicPartition> topicPartitions = awaitTopicPartitions(topic, consumer);
            if (!consumer.assignment().containsAll(topicPartitions) || fromBeginning) {
                consumer.assign(topicPartitions);
                consumer.seekToBeginning(topicPartitions);
                consumer.commitSync();
            }
            try {
                ConsumerRecords<?, ?> records = consumer.poll(Duration.ofSeconds(1));
                List<Map<String, Object>> consumerRecords = consumerRecordsToMaps(records);
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
                        log.debug("offset was %s for topic %s".formatted(offset, topicPartition));
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
                    consumer.seekToBeginning(topicPartitions);
                    consumer.commitSync();
                }
                consumer.seek(new TopicPartition(topic, 0), 0);
                ConsumerRecords<?, ?> records = consumer.poll(Duration.ofSeconds(1));
                try {
                    assertThat(records.count()).isEqualTo(amount);
                } catch (AssertionError e) {
                    List<Map<String, Object>> consumerRecords = consumerRecordsToMaps(records);
                    log.error("Kafka assertion failed for topic '{}'. Expected {} messages but found {}. Actual messages:\n{}", 
                            topic, amount, records.count(), Mapper.toYaml(consumerRecords));
                    throw e;
                }
            }
        });
    }

    @Given(THAT + "we disable kafka interceptor$")
    public void disable_kafka_interceptor() {
        KafkaInterceptor.disable();
    }

    @Given(THAT + "we enable kafka interceptor$")
    public void enable_kafka_interceptor() {
        KafkaInterceptor.enable();
    }

    private List<SendResult<?, ?>> publish(String name, String topic, Object content, String key) {
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

    private List<SendResult<?, ?>> publishAvro(String name, String topic, List<Map<?, Object>> records, String key) {
        Schema schema = getSchema(name.toLowerCase(ROOT));
        List<SendResult<?, ?>> messages;
        if (key != null) {
            messages = records
                    .stream()
                    .map(avroRecord -> {
                        Schema schemaKey = getSchema(key.toLowerCase(ROOT));
                        ProducerRecord<GenericRecord, GenericRecord> producerRecord = mapToAvroKeyMessageRecord(schema, schemaKey, topic, avroRecord);
                        return blockingSend(avroKeyMessageKafkaTemplate, producerRecord);
                    }).collect(Collectors.toList());
            avroKeyMessageKafkaTemplate.flush();
        } else {
            messages = records
                    .stream()
                    .map(avroRecord -> {
                        ProducerRecord<String, GenericRecord> producerRecord = mapToAvroRecord(schema, topic, (Map<String, Object>) avroRecord);
                        return blockingSend(avroKafkaTemplate, producerRecord);
                    }).collect(Collectors.toList());
            avroKafkaTemplate.flush();
        }
        return messages;
    }

    private ProducerRecord<GenericRecord, GenericRecord> mapToAvroKeyMessageRecord(Schema schemaMessage, Schema schemaKey, String topic, Map<?, Object> avroRecord) {
        GenericRecord genericRecordMessage = buildGenericRecordMessage(schemaMessage, avroRecord);

        GenericRecordBuilder genericRecordBuilderKey = new GenericRecordBuilder(schemaKey);
        Map<String, Object> keyValue = (Map<String, Object>) avroRecord.get("key");
        keyValue.forEach((fieldName, value) -> genericRecordBuilderKey.set(fieldName, wrapIn(value, schemaKey.getField(fieldName).schema())));
        GenericData.Record recordKey = genericRecordBuilderKey.build();

        ProducerRecord<GenericRecord, GenericRecord> producerRecord = new ProducerRecord<>(topic, recordKey, genericRecordMessage);
        ((Map<String, String>) avroRecord.get("headers"))
                .forEach((key, value) -> producerRecord.headers().add(key, value.getBytes(UTF_8)));

        return producerRecord;
    }

    private ProducerRecord<String, GenericRecord> mapToAvroRecord(Schema schema, String topic, Map<String, Object> avroRecord) {
        GenericRecord genericRecordMessage = buildGenericRecordMessage(schema, avroRecord);

        String messageKey = (String) avroRecord.get("key");

        ProducerRecord<String, GenericRecord> producerRecord = new ProducerRecord<>(topic, messageKey, genericRecordMessage);
        ((Map<String, String>) avroRecord.get("headers"))
                .forEach((key, value) -> producerRecord.headers().add(key, value != null ? value.getBytes(UTF_8) : null));

        return producerRecord;
    }

    private @NotNull GenericRecord buildGenericRecordMessage(Schema schemaMessage, Map<?, Object> avroRecord) {
        GenericRecordBuilder genericRecordBuilderMessage = new GenericRecordBuilder(schemaMessage);
        if (avroRecord.get("value") != null) {
            ((Map<String, Object>) avroRecord.get("value"))
                    .forEach((fieldName, value) -> genericRecordBuilderMessage.set(fieldName, wrapIn(value, schemaMessage.getField(fieldName).schema())));
        }
        return genericRecordBuilderMessage.build();
    }

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

    @NotNull
    private List<SendResult<?, ?>> publishJson(String topic, List<Map<?, Object>> records) {
        List<SendResult<?, ?>> messages = records
                .stream()
                .map(jsonRecord -> blockingSend(jsonKafkaTemplate, mapToJsonRecord(topic, jsonRecord))).collect(Collectors.toList());
        jsonKafkaTemplate.flush();
        return messages;
    }

    public ProducerRecord<String, String> mapToJsonRecord(String topic, Map<?, Object> jsonRecord) {
        String messageKey = (String) jsonRecord.get("key");
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, messageKey, Mapper.toJson(jsonRecord.get("value")));
        ((Map<String, String>) jsonRecord.get("headers"))
                .forEach((key, value) -> producerRecord.headers().add(key, value!=null ? value.getBytes(UTF_8):null));

        return producerRecord;
    }

    private @NotNull Consumer<?, ?> getConsumer(String name, String topic) {
        Consumer<?, ?> consumer;
        if (isJsonMessageType(name)) {
            consumer = getJsonConsumer(topic);
        } else {
            consumer = getAvroConsumer(topic);
        }
        assertThat(consumer).overridingErrorMessage("""
                Kafka message consumption failed. A KafkaConsumer is missing from your Spring application context.
                To fix this, define a KafkaConsumer<KEY, VALUE> bean in your Spring configuration.
                Use GenericRecord for Avro or String for JSON as KEY and VALUE types.
                """).isNotNull();
        return consumer;
    }

    public List<Consumer<?, ?>> getAllConsumers(String topic) {
        return Stream.concat(getAvroJacksonConsumers(topic).stream(), Stream.of(getAvroConsumer(topic), getJsonConsumer(topic))).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<Consumer<Object, Object>> getAvroJacksonConsumers(String topic) {
        if (avroJacksonConsumerFactories == null) {
            return null;
        }
        return avroJacksonConsumers.computeIfAbsent(topic, t -> avroJacksonConsumerFactories.stream()
                .map(avroJacksonConsumerFactory -> avroJacksonConsumerFactory.createConsumer(UUID.randomUUID() + "_avro_jackson_" + t, ""))
                .collect(Collectors.toList()));
    }

    public Consumer<?, GenericRecord> getAvroConsumer(String topic) {
        if (avroConsumerFactories.isEmpty()) {
            return null;
        }
        return avroConsumers.computeIfAbsent(topic, t -> avroConsumerFactories.get(0).createConsumer(UUID.randomUUID() + "_avro_" + t, ""));
    }

    public Consumer<String, String> getJsonConsumer(String topic) {
        if (jsonConsumerFactories.isEmpty()) {
            return null;
        }
        return jsonConsumers.computeIfAbsent(topic, t -> this.jsonConsumerFactories.get(0).createConsumer(UUID.randomUUID() + "_json_" + t, ""));
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

    private List<Map<String, Object>> consumerRecordsToMaps(ConsumerRecords<?, ?> records) {
        return StreamSupport.stream(records.spliterator(), false)
                .map(record -> {
                    Map<String, String> headers = Stream.of(record.headers().toArray())
                            .collect(HashMap::new,
                                    (map, header) -> map.put(header.key(), header.value() != null ? new String(header.value()) : null),
                                    HashMap::putAll);
                    Map<String, Object> value = Mapper.read(record.value().toString());
                    String messageKey = record.key() != null ? String.valueOf(record.key()) : "";
                    return Map.of("value", value, "headers", headers, "key", messageKey);
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
                                "- ensure that the schema .avsc file has been correctly added using the 'avro schema' step. Doc: https://github.com/Decathlon/tzatziki/tree/main/tzatziki-spring-kafka#defining-an-avro-schema\n" +
                                "- confirm that the object '" + name + "' in your step matches the value of the 'name' property defined in the Avro schema.\n")
                .isInstanceOf(Schema.class);
        return (Schema) schema;
    }

    private <K, V> SendResult<K, V> blockingSend(KafkaTemplate<K, V> kafkaTemplate, ProducerRecord<K, V> producerRecord) {
        assertThat(kafkaTemplate)
                .overridingErrorMessage("""
                        Kafka message send failed. A KafkaTemplate is missing from your Spring application context.
                        To fix this, define a KafkaTemplate<KEY, VALUE> bean in your Spring configuration.
                        Use GenericRecord for Avro or String for JSON as KEY and VALUE types.""")
                .isNotNull();
        CompletableFuture<SendResult<K, V>> sendReturn = Methods.invokeUnchecked(kafkaTemplate, Methods.getMethod(KafkaTemplate.class, "send", ProducerRecord.class), producerRecord);
        return sendReturn.join();
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

  @ignore
  Scenario: we wait for a poll to occur on a specific topic
    When the json-users-input topic was just polled

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
```
