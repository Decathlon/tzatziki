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

    private static KafkaProducer<String, GenericRecord> avroProducer;
    private static KafkaProducer<GenericRecord, GenericRecord> avroKeyMessageProducer;
    private static KafkaProducer<String, String> jsonProducer;

    // ===== Producing =====

    @Override
    public RecordMetadata sendAvro(ProducerRecord<String, GenericRecord> record) {
        try {
            return getAvroProducer().send(record).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to send Avro record", e);
        }
    }

    @Override
    public RecordMetadata sendAvroKeyMessage(ProducerRecord<GenericRecord, GenericRecord> record) {
        try {
            return getAvroKeyMessageProducer().send(record).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to send Avro key message record", e);
        }
    }

    @Override
    public RecordMetadata sendJson(ProducerRecord<String, String> record) {
        try {
            return getJsonProducer().send(record).get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to send JSON record", e);
        }
    }

    @Override
    public void flushAvroProducer() {
        if (avroProducer != null) avroProducer.flush();
    }

    @Override
    public void flushAvroKeyMessageProducer() {
        if (avroKeyMessageProducer != null) avroKeyMessageProducer.flush();
    }

    @Override
    public void flushJsonProducer() {
        if (jsonProducer != null) jsonProducer.flush();
    }

    // ===== Consuming =====

    @Override
    public Consumer<String, GenericRecord> getAvroConsumer(String topic) {
        return avroConsumers.computeIfAbsent(topic, t -> {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID() + "_avro_" + t);
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, KafkaConfigurationProperties.getConsumerAutoOffsetReset());
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
            props.put("schema.registry.url", KafkaConfigurationProperties.getSchemaRegistryUrl());
            return new KafkaConsumer<>(props);
        });
    }

    @Override
    public Consumer<String, String> getJsonConsumer(String topic) {
        return jsonConsumers.computeIfAbsent(topic, t -> {
            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID() + "_json_" + t);
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, KafkaConfigurationProperties.getConsumerAutoOffsetReset());
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
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

    // ===== Admin =====

    @Override
    public Map<String, Object> adminProperties() {
        return Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
    }

    // ===== Producer creation =====

    private synchronized KafkaProducer<String, GenericRecord> getAvroProducer() {
        if (avroProducer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", KafkaConfigurationProperties.getSchemaRegistryUrl());
            avroProducer = new KafkaProducer<>(props);
        }
        return avroProducer;
    }

    private synchronized KafkaProducer<GenericRecord, GenericRecord> getAvroKeyMessageProducer() {
        if (avroKeyMessageProducer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
            props.put("schema.registry.url", KafkaConfigurationProperties.getSchemaRegistryUrl());
            avroKeyMessageProducer = new KafkaProducer<>(props);
        }
        return avroKeyMessageProducer;
    }

    private synchronized KafkaProducer<String, String> getJsonProducer() {
        if (jsonProducer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            jsonProducer = new KafkaProducer<>(props);
        }
        return jsonProducer;
    }
}
