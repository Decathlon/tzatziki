package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.kafka.KafkaInterceptor;
import com.decathlon.tzatziki.kafka.SchemaRegistry;
import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Mapper;
import com.decathlon.tzatziki.utils.MockFaster;
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
import org.apache.kafka.common.header.Header;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.decathlon.tzatziki.kafka.KafkaInterceptor.offsets;
import static com.decathlon.tzatziki.utils.Asserts.awaitUntilAsserted;
import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static io.semla.util.Unchecked.unchecked;
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
    private static final Map<String, Consumer<String, String>> jsonConsumers = new LinkedHashMap<>();
    private static final Set<String> topicsToAutoSeek = new LinkedHashSet<>();

    private static boolean isStarted;

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

    public static String bootstrapServers() {
        return embeddedKafka.getBrokersAsString();
    }

    private final ObjectSteps objects;

    @Autowired(required = false)
    private KafkaTemplate<String, GenericRecord> avroKafkaTemplate;

    @Autowired(required = false)
    private KafkaTemplate<String, String> jsonKafkaTemplate;

    @Autowired(required = false)
    List<ConsumerFactory<String, Object>> avroJacksonConsumerFactories = new ArrayList<>();

    @Autowired(required = false)
    ConsumerFactory<String, GenericRecord> avroConsumerFactory;

    @Autowired(required = false)
    ConsumerFactory<String, String> jsonConsumerFactory;

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
            objects.add("kafka.schemas." + name.toLowerCase(ROOT), schema);
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
            List<RecordMetadata> results = publish(name, topic, content)
                    .parallelStream()
                    .map(KafkaInterceptor::waitUntilProcessed)
                    .map(SendResult::getRecordMetadata)
                    .collect(Collectors.toList());
            log.debug("processed {}", results);
            KafkaInterceptor.awaitForSuccessfullOnly = false;
        });
    }

    @SneakyThrows
    @When(THAT + GUARD + "the " + VARIABLE + " group id has fully consumed the " + VARIABLE + " topic$")
    public void topic_has_been_consumed_on_every_partition(Guard guard, String groupId, String topic) {
        guard.in(objects, () -> awaitUntilAsserted(() -> getAllConsumers(topic).forEach(consumer -> unchecked(() -> {
            try (Admin admin = Admin.create(avroConsumerFactory.getConfigurationProperties())) {
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

    @Given(THAT + "the current offset of " + VARIABLE + " on the topic " + VARIABLE + " is (\\d+)$")
    public void that_the_current_offset_the_groupid_on_topic_is(String groupId, String topic, long offset) throws ExecutionException, InterruptedException {
        try (Admin admin = Admin.create(avroConsumerFactory.getConfigurationProperties())) {
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

    @Then(THAT + GUARD + "the " + VARIABLE + " topic contains" + COMPARING_WITH + " " + A + RECORD + ":$")
    public void the_topic_contains(Guard guard, String topic, Comparison comparison, String name, String content) {
        guard.in(objects, () -> {
            Consumer<String, ?> consumer = getConsumer(name, topic);
            List<TopicPartition> topicPartitions = awaitTopicPartitions(topic, consumer);
            if (!consumer.assignment().containsAll(topicPartitions)) {
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
                            Map<String, Object> value = Mapper.read(record.value().toString());
                            return Map.<String, Object>of("value", value, "headers", headers);
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
                    consumer.commitSync();
                }
                awaitUntilAsserted(() ->
                        assertThat(consumer.endOffsets(topicPartitions).get(topicPartitions.get(0))).isEqualTo(amount)
                );
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

    private boolean isJsonMessageType(String name) {
        return name.matches("json messages?");
    }

    private List<SendResult<String, ?>> publishAvro(String name, String topic, List<Map<String, Object>> records) {
        Schema schema = getSchema(name.toLowerCase(ROOT));
        List<SendResult<String, ?>> messages = records
                .stream()
                .map(record -> {
                    GenericRecordBuilder genericRecordBuilder = new GenericRecordBuilder(schema);
                    ((Map<String, Object>) record.get("value"))
                            .forEach((fieldName, value) -> genericRecordBuilder.set(fieldName, wrapIn(value, schema.getField(fieldName).schema())));
                    ProducerRecord<String, GenericRecord> producerRecord = new ProducerRecord<>(topic, genericRecordBuilder.build());
                    ((Map<String, String>) record.get("headers"))
                            .forEach((key, value) -> producerRecord.headers().add(key, value.getBytes(UTF_8)));
                    return avroKafkaTemplate.send(producerRecord).completable().join();
                }).collect(Collectors.toList());
        avroKafkaTemplate.flush();
        return messages;
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
        if (value instanceof String && !schema.getType().equals(Schema.Type.STRING)) {
            value = parseAvro((String) value, schema);
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
    private List<SendResult<String, ?>> publishJson(String topic, List<Map<String, Object>> records) {
        List<SendResult<String, ?>> messages = records
                .stream()
                .map(record -> {
                    ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, Mapper.toJson(record.get("value")));
                    ((Map<String, String>) record.get("headers"))
                            .forEach((key, value) -> producerRecord.headers().add(key, value.getBytes(UTF_8)));
                    return jsonKafkaTemplate.send(producerRecord).completable().join();
                }).collect(Collectors.toList());
        jsonKafkaTemplate.flush();
        return messages;
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
        if (avroConsumerFactory == null) {
            return null;
        }
        return avroConsumers.computeIfAbsent(topic, t -> avroConsumerFactory.createConsumer(UUID.randomUUID() + "_avro_" + t, ""));
    }

    public Consumer<String, String> getJsonConsumer(String topic) {
        if (jsonConsumerFactory == null) {
            return null;
        }
        return jsonConsumers.computeIfAbsent(topic, t -> this.jsonConsumerFactory.createConsumer(UUID.randomUUID() + "_json_" + t, ""));
    }

    @NotNull
    private List<TopicPartition> awaitTopicPartitions(String topic, Consumer<String, ?> consumer) {
        List<TopicPartition> topicPartitions = new ArrayList<>();
        awaitUntilAsserted(() -> {
            Map<String, List<PartitionInfo>> partitionsByTopic = consumer.listTopics();
            assertThat(partitionsByTopic).containsKey(topic);
            topicPartitions.addAll(partitionsByTopic.get(topic)
                    .stream()
                    .map(partitionInfo -> new TopicPartition(partitionInfo.topic(), partitionInfo.partition()))
                    .collect(Collectors.toList()));
        });
        return topicPartitions;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asListOfRecordsWithHeaders(Object content) {
        List<Map<String, Object>> records = content instanceof Map
                ? List.of((Map<String, Object>) content)
                : (List<Map<String, Object>>) content;
        return records.stream()
                .map(record -> {
                    if (record.size() == 2 && record.containsKey("value") && record.containsKey("headers")) {
                        return record;
                    }
                    return Map.<String, Object>of("value", record, "headers", new LinkedHashMap<>());
                }).collect(Collectors.toList());
    }

    private Schema getSchema(String name) {
        Object schema = objects.getOrSelf("kafka.schemas." + name);
        if (schema instanceof String && name.endsWith("s")) {
            schema = objects.getOrSelf("kafka.schemas." + name.substring(0, name.length() - 1));
        }
        assertThat(schema).isInstanceOf(Schema.class);
        return (Schema) schema;
    }
}
