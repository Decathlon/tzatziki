package com.decathlon.tzatziki.kafka;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
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
     * Returns the physical base offset for a topic partition — the real Kafka offset at
     * which the current test's messages begin. This value is used in Admin API operations
     * (e.g. {@code alterConsumerGroupOffsets}) where the broker expects absolute offsets.
     * <p>
     * In plain Kafka this equals the end-offset recorded before the scenario started.
     * In Spring Kafka the AOP consumer proxy records this value in {@code KafkaInterceptor}.
     */
    long adjustedOffsetFor(TopicPartition tp);

    /**
     * Returns the offset to pass when seeking a <em>test assertion consumer</em>.
     * <p>
     * <b>Plain Kafka:</b> identical to {@link #adjustedOffsetFor} — the consumer must
     * seek to the physical offset because there is no proxy layer.<br>
     * <b>Spring Kafka:</b> returns {@code 0} because the AOP consumer proxy in
     * {@code KafkaInterceptor} transparently rewrites all offsets, giving each test a
     * virtual offset space starting at 0.
     */
    long consumerSeekOffset(TopicPartition tp);

    Map<TopicPartition, Long> pastOffsets();

    void seekConsumerToTestStart(Consumer<?, ?> consumer, String topic);

    /**
     * Seeks all consumers (avro + json) to the end of the given topic
     * and records the position as the new baseline for subsequent assertions.
     */
    void seekAllToEnd(String topic);

    /**
     * Disables offset management for the current backend.
     * <p>
     * When disabled, offset tracking is bypassed — consumers will see messages from
     * the very beginning of the topic rather than from the current test's baseline.
     * <p>
     * <b>Plain Kafka:</b> disables {@code KafkaOffsetManager}.<br>
     * <b>Spring Kafka:</b> disables {@code KafkaInterceptor} (AOP proxy offset rewriting).
     */
    void disableOffsetManagement();

    /**
     * Re-enables offset management for the current backend.
     *
     * @see #disableOffsetManagement()
     */
    void enableOffsetManagement();

    // ===== Admin =====

    Map<String, Object> adminProperties();

    // ===== Lifecycle =====

    /**
     * Closes all Kafka clients (producers, consumers) and resets internal state.
     * Called once after all scenarios via {@code @AfterAll} to prevent resource leaks.
     */
    default void cleanup() {
        // no-op by default — backends with closeable resources must override
    }
}
