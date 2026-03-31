package com.decathlon.tzatziki.kafka;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface abstracting the operational differences between plain Kafka and Spring Kafka.
 * <p>
 * {@code PlainKafkaBackend} (in tzatziki-kafka) uses direct {@code KafkaProducer}/{@code KafkaConsumer}.
 * {@code SpringKafkaBackend} (in tzatziki-spring-kafka) uses {@code KafkaTemplate}/{@code ConsumerFactory}.
 */
public interface KafkaBackend {

    // ===== Producing =====

    RecordMetadata sendAvro(ProducerRecord<String, GenericRecord> record);

    RecordMetadata sendAvroKeyMessage(ProducerRecord<GenericRecord, GenericRecord> record);

    RecordMetadata sendJson(ProducerRecord<String, String> record);

    void flushAvroProducer();

    void flushAvroKeyMessageProducer();

    void flushJsonProducer();

    // ===== Consuming (for assertions) =====

    Consumer<String, GenericRecord> getAvroConsumer(String topic);

    Consumer<String, String> getJsonConsumer(String topic);

    List<Consumer<?, ?>> getAllConsumers(String topic);

    // ===== Offset management =====

    void beforeScenario(Set<String> topicsToAutoSeek);

    /**
     * Returns the physical base offset for a topic partition (for admin API operations).
     */
    long adjustedOffsetFor(TopicPartition tp);

    /**
     * Returns the offset to use in consumer seek operations.
     * For plain Kafka: same as adjustedOffsetFor (physical offset).
     * For Spring Kafka: 0 (the consumer proxy transparently adjusts offsets).
     */
    long consumerSeekOffset(TopicPartition tp);

    Map<TopicPartition, Long> pastOffsets();

    void seekConsumerToTestStart(Consumer<?, ?> consumer, String topic);

    List<ConsumerRecord<?, ?>> filterForCurrentTest(ConsumerRecords<?, ?> records);

    /**
     * Seeks all consumers (avro + json) to the end of the given topic
     * and records the position as the new baseline for subsequent assertions.
     */
    void seekAllToEnd(String topic);

    /**
     * Seeks all consumers (avro + json) to the beginning of the given topic.
     */
    void seekAllToBeginning(String topic);

    // ===== Admin =====

    Map<String, Object> adminProperties();
}
