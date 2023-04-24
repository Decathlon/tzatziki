package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.kafka.KafkaInterceptor;
import com.decathlon.tzatziki.kafka.SchemaRegistry;
import com.decathlon.tzatziki.utils.*;
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
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.util.concurrent.ListenableFuture;

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
@SuppressWarnings({"SpringJavaAutowiredMembersInspection", "unchecked"})
public class KafkaSteps {

    public static final String RECORD = "(json messages?|" + VARIABLE_PATTERN + ")";
    private static final EmbeddedKafkaBroker embeddedKafka = new EmbeddedKafkaBroker(1, true, 1);

    private static final Map<String, List<Consumer<String, Object>>> avroJacksonConsumers = new LinkedHashMap<>();
    private static final Map<String, Consumer<String, GenericRecord>> avroConsumers = new LinkedHashMap<>();
    private static final Map<String, Consumer<String, Object>> jsonConsumers = new LinkedHashMap<>();
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
        SchemaRegistry.initialize();
    }

    public static void doNotWaitForMembersOn(String topic) {
        checkedTopics.add(topic);
    }

    public static String bootstrapServers() {
        return embeddedKafka.getBrokersAsString();
    }

    private final ObjectSteps objects;

    @Autowired(required = false)
    private KafkaTemplate<String, GenericRecord> avroKafkaTemplate;

    @Autowired(required = false)
    private KafkaTemplate<String, Object> jsonKafkaTemplate;

    @Autowired(required = false)
    List<ConsumerFactory<String, Object>> avroJacksonConsumerFactories = new ArrayList<>();

    @Autowired(required = false)
    List<ConsumerFactory<String, GenericRecord>> avroConsumerFactories = new ArrayList<>();

    @Autowired(required = false)
    List<ConsumerFactory<String, Object>> jsonConsumerFactories = new ArrayList<>();

    @Autowired(required = false)
    private KafkaListenerEndpointRegistry registry;


    public KafkaSteps(ObjectSteps objects) {
        this.objects = objects;
    }

    public static String schemaRegistryUrl() {
        return MockFaster.url() + SchemaRegistry.endpoint;
    }

    public static void autoSeekTopics(String... topics) {
        topicsToAutoSeek.addAll(Arrays.asList(topics));
    }

    @Before
    public void before() {
        SchemaRegistry.initialize();
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
    @When(THAT + GUARD + A + RECORD + " (?:is|are)? published on the " + VARIABLE + " topic:$")
    public void we_publish_on_a_topic(Guard guard, String name, String topic, Object content) {
        guard.in(objects, () -> publish(name, topic, content));
    }

    @Deprecated(forRemoval = true)
    @When(THAT + GUARD + A + RECORD + " (?:is|are)? received on the " + VARIABLE + " topic:$")
    public void a_message_is_received_on_a_topic(Guard guard, String name, String topic, Object content) {
        a_message_is_consumed_from_a_topic(guard, name, false, topic, content);
    }

    @Deprecated(forRemoval = true)
    @When(THAT + GUARD + A_USER + "receives? " + A + VARIABLE + " on the topic " + VARIABLE + ":$")
    public void we_receive_a_message_on_a_topic(Guard guard, String name, String topic, Object content) {
        a_message_is_consumed_from_a_topic(guard, name, false, topic, content);
    }


    @SneakyThrows
    @When(THAT + GUARD + A + RECORD + " (?:is|are)? (successfully )?consumed from the " + VARIABLE + " topic:$")
    public void a_message_is_consumed_from_a_topic(Guard guard, String name, boolean successfully, String topic, Object content) {
        guard.in(objects, () -> {
            KafkaInterceptor.awaitForSuccessfullOnly = successfully;
            if (!checkedTopics.contains(topic)) {
                try (Admin admin = Admin.create(getAnyConsumerFactory().getConfigurationProperties())) {
                    awaitUntil(() -> {
                        List<String> groupIds = admin.listConsumerGroups().all().get().stream().map(ConsumerGroupListing::groupId).toList();
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
            List<RecordMetadata> results = publish(name, topic, content)
                    .parallelStream()
                    .map(KafkaInterceptor::waitUntilProcessed)
                    .map(SendResult::getRecordMetadata)
                    .collect(Collectors.toList());
            log.debug("processed {}", results);
            KafkaInterceptor.awaitForSuccessfullOnly = false;
        });
    }

    @NotNull
    private ConsumerFactory<String, ?> getAnyConsumerFactory() {
        return Stream.concat(Stream.concat(jsonConsumerFactories.stream(), avroConsumerFactories.stream()), avroJacksonConsumerFactories.stream()).findFirst().get();
    }

    @SneakyThrows
    @When(THAT + GUARD + "the " + VARIABLE + " group id has fully consumed the " + VARIABLE + " topic$")
    public void topic_has_been_consumed_on_every_partition(Guard guard, String groupId, String topic) {
        guard.in(objects, () -> awaitUntilAsserted(() -> getAllConsumers(topic).forEach(consumer -> unchecked(() -> {
            try (Admin admin = Admin.create(avroConsumerFactories.get(0).getConfigurationProperties())) {
                Map<TopicPartition, OffsetAndMetadata> topicPartitionOffsetAndMetadataMap = admin
                        .listConsumerGroupOffsets(groupId)
                        .partitionsToOffsetAndMetadata().get();
                TopicPartition key = new TopicPartition(topic, 0);
                if (topicPartitionOffsetAndMetadataMap.containsKey(key)) {
                    long offset = topicPartitionOffsetAndMetadataMap.get(key).offset();
                    consumer.endOffsets(List.of(key))
                            .forEach((topicPartition, endOffset) -> Assert.assertEquals((long) endOffset, offset));
                } else {
                    throw new AssertionError("let's wait a bit more");
                }
            }
        }))));
    }

    @When(THAT + GUARD + "the " + VARIABLE + " topic was just polled$")
    public void topic_was_just_polled(Guard guard, String topic) {
        guard.in(objects, () -> {
            Semaphore semaphore = new Semaphore(0);
            semaphoreByTopic.put(topic, semaphore);
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Assertions.fail(e);
            }
        });
    }

    @Given(THAT + "the current offset of " + VARIABLE + " on the topic " + VARIABLE + " is (\\d+)$")
    public void that_the_current_offset_the_groupid_on_topic_is(String groupId, String topic, long offset) throws ExecutionException, InterruptedException {
        try (Admin admin = Admin.create(avroConsumerFactories.get(0).getConfigurationProperties())) {
            admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
            TopicPartition topicPartition = new TopicPartition(topic, 0);
            Collection<MemberDescription> members = admin.describeConsumerGroups(List.of(groupId)).describedGroups().get(groupId).get().members();
            if (!members.isEmpty()) {
                try {
                    admin.removeMembersFromConsumerGroup(groupId, new RemoveMembersFromConsumerGroupOptions()).all().get();
                } catch (InterruptedException e) {
                    // Restore interrupted state...
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // this could happen if the consumer group emptied itself meanwhile
                }
            }
            admin.alterConsumerGroupOffsets(groupId, Map.of(topicPartition, new OffsetAndMetadata(offset + KafkaInterceptor.adjustedOffsetFor(topicPartition))))
                    .partitionResult(topicPartition)
                    .get();
        }
    }

    @Then(THAT + GUARD + "(from the beginning )?the " + VARIABLE + " topic contains" + COMPARING_WITH + " " + A + RECORD + ":$")
    public void the_topic_contains(Guard guard, boolean fromBeginning, String topic, Comparison comparison, String name, String content) {
        guard.in(objects, () -> {
            Consumer<String, ?> consumer = getConsumer(name, topic);
            List<TopicPartition> topicPartitions = awaitTopicPartitions(topic, consumer);
            if (!consumer.assignment().containsAll(topicPartitions) || fromBeginning) {
                consumer.assign(topicPartitions);
                consumer.seekToBeginning(topicPartitions);
                consumer.commitSync();
            }
            try {
                ConsumerRecords<String, ?> records = consumer.poll(Duration.ofSeconds(1));
                List<Map<String, Object>> consumerRecords = StreamSupport.stream(records.spliterator(), false)
                        .map(record -> {
                            Map<String, String> headers = Stream.of(record.headers().toArray())
                                    .collect(Collectors.toMap(Header::key, header -> new String(header.value())));
                            String messageKey = ofNullable(record.key()).orElse("");
                            Object value = record.value();
                            if (value instanceof GenericData.Record) {
                                value = Mapper.read(value.toString());
                            }
                            return Map.of("value", value, "headers", headers, "key", messageKey);
                        })
                        .collect(Collectors.toList());
                comparison.compare(consumerRecords, asListOfRecordsWithHeaders(Mapper.read(objects.resolve(content))));
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

    @Then(THAT + GUARD + "the " + VARIABLE + " topic contains (\\d+) " + RECORD + "?$")
    public void the_topic_contains_n_messages(Guard guard, String topic, int amount, String name) {
        guard.in(objects, () -> {
            Consumer<String, ?> consumer = getConsumer(name, topic);
            if (amount != 0 || consumer.listTopics().containsKey(topic)) {
                List<TopicPartition> topicPartitions = awaitTopicPartitions(topic, consumer);
                if (!consumer.assignment().containsAll(topicPartitions)) {
                    consumer.assign(topicPartitions);
                    consumer.seekToBeginning(topicPartitions);
                    consumer.commitSync();
                }
                consumer.seek(new TopicPartition(topic, 0), 0);
                ConsumerRecords<String, ?> records = consumer.poll(Duration.ofSeconds(1));
                assertThat(records.count()).isEqualTo(amount);
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

    private List<SendResult<String, ?>> publish(String name, String topic, Object content) {
        List<Map<String, Object>> records = asListOfRecordsWithHeaders(Mapper.read(objects.resolve(content)));
        log.debug("publishing {}", records);
        if (isJsonMessageType(name)) {
            return publishJson(topic, records);
        }
        return publishAvro(name, topic, records);
    }

    public boolean isJsonMessageType(String name) {
        return name.matches("json messages?");
    }

    private List<SendResult<String, ?>> publishAvro(String name, String topic, List<Map<String, Object>> records) {
        Schema schema = getSchema(name.toLowerCase(ROOT));
        List<SendResult<String, ?>> messages = records
                .stream()
                .map(avroRecord -> {
                    ProducerRecord<String, GenericRecord> producerRecord = mapToAvroRecord(schema, topic, avroRecord);

                    return blockingSend(avroKafkaTemplate, producerRecord);
                }).collect(Collectors.toList());
        avroKafkaTemplate.flush();
        return messages;
    }

    public ProducerRecord<String, GenericRecord> mapToAvroRecord(Schema schema, String topic, Map<String, Object> avroRecord) {
        GenericRecordBuilder genericRecordBuilder = new GenericRecordBuilder(schema);
        ((Map<String, Object>) avroRecord.get("value"))
                .forEach((fieldName, value) -> genericRecordBuilder.set(fieldName, wrapIn(value, schema.getField(fieldName).schema())));

        String messageKey = (String) avroRecord.get("key");

        ProducerRecord<String, GenericRecord> producerRecord = new ProducerRecord<>(topic, messageKey, genericRecordBuilder.build());

        ((Map<String, String>) avroRecord.get("headers"))
                .forEach((key, value) -> producerRecord.headers().add(key, value.getBytes(UTF_8)));

        return producerRecord;
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

    private List<SendResult<String, ?>> publishJson(String topic, List<Map<String, Object>> records) {
        List<SendResult<String, ?>> messages = records
                .stream()
                .map(jsonRecord -> blockingSend(jsonKafkaTemplate, mapToJsonRecord(topic, jsonRecord))).collect(Collectors.toList());
        jsonKafkaTemplate.flush();
        return messages;
    }

    public ProducerRecord<String, Object> mapToJsonRecord(String topic, Map<String, Object> jsonRecord) {
        String messageKey = (String) jsonRecord.get("key");
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(topic, messageKey, jsonRecord.get("value"));
        ((Map<String, String>) jsonRecord.get("headers"))
                .forEach((key, value) -> producerRecord.headers().add(key, value.getBytes(UTF_8)));

        return producerRecord;
    }

    private Consumer<String, ?> getConsumer(String name, String topic) {
        if (isJsonMessageType(name)) {
            return getJsonConsumer(topic);
        }
        return getAvroConsumer(topic);
    }

    public List<Consumer<String, ?>> getAllConsumers(String topic) {
        return Stream.concat(getAvroJacksonConsumers(topic).stream(), Stream.of(getAvroConsumer(topic), getJsonConsumer(topic))).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<Consumer<String, Object>> getAvroJacksonConsumers(String topic) {
        if (avroJacksonConsumerFactories == null) {
            return null;
        }
        return avroJacksonConsumers.computeIfAbsent(topic, t -> avroJacksonConsumerFactories.stream()
                .map(avroJacksonConsumerFactory -> avroJacksonConsumerFactory.createConsumer(UUID.randomUUID() + "_avro_jackson_" + t, ""))
                .collect(Collectors.toList()));
    }

    public Consumer<String, GenericRecord> getAvroConsumer(String topic) {
        if (avroConsumerFactories.isEmpty()) {
            return null;
        }
        return avroConsumers.computeIfAbsent(topic, t -> avroConsumerFactories.get(0).createConsumer(UUID.randomUUID() + "_avro_" + t, ""));
    }

    public Consumer<String, Object> getJsonConsumer(String topic) {
        if (jsonConsumerFactories.isEmpty()) {
            return null;
        }
        return jsonConsumers.computeIfAbsent(topic, t -> this.jsonConsumerFactories.get(0).createConsumer(UUID.randomUUID() + "_json_" + t, ""));
    }

    @NotNull
    private List<TopicPartition> awaitTopicPartitions(String topic, Consumer<String, ?> consumer) {
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

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> asListOfRecordsWithHeaders(Object content) {
        List<Map<String, Object>> records = content instanceof Map
                ? List.of((Map<String, Object>) content)
                : (List<Map<String, Object>>) content;
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
        if (schema instanceof String && name.endsWith("s")) {
            schema = objects.getOrSelf("_kafka.schemas." + name.substring(0, name.length() - 1));
        }
        assertThat(schema).isInstanceOf(Schema.class);
        return (Schema) schema;
    }

    private <K, V> SendResult<K, V> blockingSend(KafkaTemplate<K, V> kafkaTemplate, ProducerRecord<K, V> producerRecord) {
        Object sendReturn = Methods.invokeUnchecked(kafkaTemplate, Methods.getMethod(KafkaTemplate.class, "send", ProducerRecord.class), producerRecord);
        CompletableFuture<SendResult<K, V>> future = sendReturn instanceof ListenableFuture listenableFuture
                ? listenableFuture.completable()
                : (CompletableFuture<SendResult<K, V>>) sendReturn;

        return future.join();
    }
}