package com.decathlon.tzatziki.kafka;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default {@link KafkaBackend} implementation using plain Apache Kafka clients.
 * Used when tzatziki-spring-kafka is NOT on the classpath.
 */
@Slf4j
public class PlainKafkaBackend implements KafkaBackend {

    private static final Map<String, Consumer<String, GenericRecord>> avroConsumers = new LinkedHashMap<>();
    private static final Map<String, Consumer<String, String>> jsonConsumers = new LinkedHashMap<>();

    private static final Map<String, KafkaProducer<String, GenericRecord>> avroProducers = new LinkedHashMap<>();
    private static final Map<String, KafkaProducer<GenericRecord, GenericRecord>> avroKeyMessageProducers = new LinkedHashMap<>();
    private static final Map<String, KafkaProducer<String, String>> jsonProducers = new LinkedHashMap<>();

    // ===== Producing =====

    @Override
    public RecordMetadata sendAvro(ProducerRecord<String, GenericRecord> record) {
        try {
            return getAvroProducer(record.topic()).send(record).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to send Avro record", e);
        }
    }

    @Override
    public RecordMetadata sendAvroKeyMessage(ProducerRecord<GenericRecord, GenericRecord> record) {
        try {
            return getAvroKeyMessageProducer(record.topic()).send(record).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to send Avro key message record", e);
        }
    }

    @Override
    public RecordMetadata sendJson(ProducerRecord<String, String> record) {
        try {
            return getJsonProducer(record.topic()).send(record).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to send JSON record", e);
        }
    }

    @Override
    public void flushAvroProducer() {
        avroProducers.values().forEach(KafkaProducer::flush);
    }

    @Override
    public void flushAvroKeyMessageProducer() {
        avroKeyMessageProducers.values().forEach(KafkaProducer::flush);
    }

    @Override
    public void flushJsonProducer() {
        jsonProducers.values().forEach(KafkaProducer::flush);
    }

    // ===== Consuming =====

    @Override
    public Consumer<String, GenericRecord> getAvroConsumer(String topic) {
        return avroConsumers.computeIfAbsent(topic, t -> {
            Properties defaults = new Properties();
            defaults.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
            defaults.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID() + "_avro_" + t);
            defaults.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, KafkaConfigurationProperties.getConsumerAutoOffsetReset());
            defaults.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            defaults.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            defaults.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
            defaults.put("schema.registry.url", KafkaConfigurationProperties.getSchemaRegistryUrl());
            Properties props = KafkaConfigurationProperties.buildProperties(defaults, t,
                    KafkaClientType.COMMON, KafkaClientType.CONSUMER, KafkaClientType.AVRO_CONSUMER);
            return new KafkaConsumer<>(props);
        });
    }

    @Override
    public Consumer<String, String> getJsonConsumer(String topic) {
        return jsonConsumers.computeIfAbsent(topic, t -> {
            Properties defaults = new Properties();
            defaults.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
            defaults.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID() + "_json_" + t);
            defaults.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, KafkaConfigurationProperties.getConsumerAutoOffsetReset());
            defaults.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            defaults.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            defaults.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            Properties props = KafkaConfigurationProperties.buildProperties(defaults, t,
                    KafkaClientType.COMMON, KafkaClientType.CONSUMER, KafkaClientType.JSON_CONSUMER);
            return new KafkaConsumer<>(props);
        });
    }

    @Override
    public List<Consumer<?, ?>> getAllConsumers(String topic) {
        return Stream.of(getAvroConsumer(topic), getJsonConsumer(topic))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ===== Offset management =====

    @Override
    public void beforeScenario(Set<String> topicsToAutoSeek) {
        KafkaOffsetManager.before();
        topicsToAutoSeek.forEach(topic -> getAllConsumers(topic).forEach(consumer ->
                KafkaOffsetManager.seekToEndAndRecord(consumer, topic)));
    }

    @Override
    public long adjustedOffsetFor(TopicPartition tp) {
        return KafkaOffsetManager.adjustedOffsetFor(tp);
    }

    @Override
    public long consumerSeekOffset(TopicPartition tp) {
        return KafkaOffsetManager.adjustedOffsetFor(tp);
    }

    @Override
    public Map<TopicPartition, Long> pastOffsets() {
        return KafkaOffsetManager.offsets();
    }

    @Override
    public void seekConsumerToTestStart(Consumer<?, ?> consumer, String topic) {
        KafkaOffsetManager.seekToTestStart(consumer, topic);
    }

    @Override
    public List<ConsumerRecord<?, ?>> filterForCurrentTest(ConsumerRecords<?, ?> records) {
        return KafkaOffsetManager.filterCurrentTestRecords(records);
    }

    @Override
    public void seekAllToEnd(String topic) {
        getAllConsumers(topic).forEach(consumer ->
                KafkaOffsetManager.seekToEndAndRecord(consumer, topic));
    }

    @Override
    public void seekAllToBeginning(String topic) {
        getAllConsumers(topic).forEach(consumer -> {
            List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                    .map(info -> new TopicPartition(info.topic(), info.partition()))
                    .collect(Collectors.toList());
            if (!consumer.assignment().containsAll(partitions)) {
                consumer.assign(partitions);
            }
            consumer.seekToBeginning(partitions);
        });
    }

    // ===== Admin =====

    @Override
    public Map<String, Object> adminProperties() {
        Properties defaults = new Properties();
        defaults.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
        Properties props = KafkaConfigurationProperties.buildProperties(defaults,
                KafkaClientType.COMMON, KafkaClientType.ADMIN);
        Map<String, Object> result = new LinkedHashMap<>();
        props.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    // ===== Producer creation (per-topic for topic-specific configuration) =====

    private synchronized KafkaProducer<String, GenericRecord> getAvroProducer(String topic) {
        return avroProducers.computeIfAbsent(topic, t -> {
            Properties defaults = new Properties();
            defaults.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
            defaults.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            defaults.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
            defaults.put("schema.registry.url", KafkaConfigurationProperties.getSchemaRegistryUrl());
            return new KafkaProducer<>(KafkaConfigurationProperties.buildProperties(defaults, t,
                    KafkaClientType.COMMON, KafkaClientType.PRODUCER, KafkaClientType.AVRO_PRODUCER));
        });
    }

    private synchronized KafkaProducer<GenericRecord, GenericRecord> getAvroKeyMessageProducer(String topic) {
        return avroKeyMessageProducers.computeIfAbsent(topic, t -> {
            Properties defaults = new Properties();
            defaults.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
            defaults.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
            defaults.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
            defaults.put("schema.registry.url", KafkaConfigurationProperties.getSchemaRegistryUrl());
            return new KafkaProducer<>(KafkaConfigurationProperties.buildProperties(defaults, t,
                    KafkaClientType.COMMON, KafkaClientType.PRODUCER, KafkaClientType.AVRO_PRODUCER));
        });
    }

    private synchronized KafkaProducer<String, String> getJsonProducer(String topic) {
        return jsonProducers.computeIfAbsent(topic, t -> {
            Properties defaults = new Properties();
            defaults.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
            defaults.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            defaults.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            return new KafkaProducer<>(KafkaConfigurationProperties.buildProperties(defaults, t,
                    KafkaClientType.COMMON, KafkaClientType.PRODUCER, KafkaClientType.JSON_PRODUCER));
        });
    }
}
