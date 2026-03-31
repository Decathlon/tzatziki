package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.steps.KafkaSteps;
import com.decathlon.tzatziki.utils.Methods;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.decathlon.tzatziki.utils.Asserts.awaitUntil;

/**
 * Spring Kafka implementation of {@link KafkaBackend}.
 * Uses {@link KafkaTemplate} for producing and {@link ConsumerFactory} for consuming.
 * Delegates offset management to {@link KafkaInterceptor} (AOP-based proxy).
 */
@Slf4j
public class SpringKafkaBackend implements KafkaBackend {

    private static final Map<String, List<Consumer<Object, Object>>> avroJacksonConsumers = new LinkedHashMap<>();
    private static final Map<String, Consumer<?, GenericRecord>> avroConsumers = new LinkedHashMap<>();
    private static final Map<String, Consumer<String, String>> jsonConsumers = new LinkedHashMap<>();

    private final KafkaTemplate<GenericRecord, GenericRecord> avroKeyMessageKafkaTemplate;
    private final KafkaTemplate<String, GenericRecord> avroKafkaTemplate;
    private final KafkaTemplate<String, String> jsonKafkaTemplate;
    private final List<ConsumerFactory<Object, Object>> avroJacksonConsumerFactories;
    private final List<ConsumerFactory<String, GenericRecord>> avroConsumerFactories;
    private final List<ConsumerFactory<String, String>> jsonConsumerFactories;

    public SpringKafkaBackend(
            KafkaTemplate<GenericRecord, GenericRecord> avroKeyMessageKafkaTemplate,
            KafkaTemplate<String, GenericRecord> avroKafkaTemplate,
            KafkaTemplate<String, String> jsonKafkaTemplate,
            List<ConsumerFactory<Object, Object>> avroJacksonConsumerFactories,
            List<ConsumerFactory<String, GenericRecord>> avroConsumerFactories,
            List<ConsumerFactory<String, String>> jsonConsumerFactories) {
        this.avroKeyMessageKafkaTemplate = avroKeyMessageKafkaTemplate;
        this.avroKafkaTemplate = avroKafkaTemplate;
        this.jsonKafkaTemplate = jsonKafkaTemplate;
        this.avroJacksonConsumerFactories = avroJacksonConsumerFactories;
        this.avroConsumerFactories = avroConsumerFactories;
        this.jsonConsumerFactories = jsonConsumerFactories;
    }

    // ===== Producing =====

    @Override
    public RecordMetadata sendAvro(ProducerRecord<String, GenericRecord> record) {
        return blockingSend(avroKafkaTemplate, record).getRecordMetadata();
    }

    @Override
    public RecordMetadata sendAvroKeyMessage(ProducerRecord<GenericRecord, GenericRecord> record) {
        return blockingSend(avroKeyMessageKafkaTemplate, record).getRecordMetadata();
    }

    @Override
    public RecordMetadata sendJson(ProducerRecord<String, String> record) {
        return blockingSend(jsonKafkaTemplate, record).getRecordMetadata();
    }

    @Override
    public void flushAvroProducer() {
        if (avroKafkaTemplate != null) avroKafkaTemplate.flush();
    }

    @Override
    public void flushAvroKeyMessageProducer() {
        if (avroKeyMessageKafkaTemplate != null) avroKeyMessageKafkaTemplate.flush();
    }

    @Override
    public void flushJsonProducer() {
        if (jsonKafkaTemplate != null) jsonKafkaTemplate.flush();
    }

    // ===== Consuming =====

    @Override
    public Consumer<String, GenericRecord> getAvroConsumer(String topic) {
        if (avroConsumerFactories.isEmpty()) {
            return null;
        }
        return (Consumer<String, GenericRecord>) avroConsumers.computeIfAbsent(topic,
                t -> avroConsumerFactories.get(0).createConsumer(UUID.randomUUID() + "_avro_" + t, ""));
    }

    @Override
    public Consumer<String, String> getJsonConsumer(String topic) {
        if (jsonConsumerFactories.isEmpty()) {
            return null;
        }
        return jsonConsumers.computeIfAbsent(topic,
                t -> jsonConsumerFactories.get(0).createConsumer(UUID.randomUUID() + "_json_" + t, ""));
    }

    @Override
    public List<Consumer<?, ?>> getAllConsumers(String topic) {
        return Stream.concat(
                getAvroJacksonConsumers(topic).stream(),
                Stream.of(getAvroConsumer(topic), getJsonConsumer(topic))
        ).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<Consumer<Object, Object>> getAvroJacksonConsumers(String topic) {
        if (avroJacksonConsumerFactories == null || avroJacksonConsumerFactories.isEmpty()) {
            return Collections.emptyList();
        }
        return avroJacksonConsumers.computeIfAbsent(topic, t -> avroJacksonConsumerFactories.stream()
                .map(factory -> factory.createConsumer(UUID.randomUUID() + "_avro_jackson_" + t, ""))
                .collect(Collectors.toList()));
    }

    // ===== Offset management =====

    @Override
    public void beforeScenario(Set<String> topicsToAutoSeek) {
        KafkaInterceptor.before();
        topicsToAutoSeek.forEach(topic -> getAllConsumers(topic).forEach(consumer -> {
            Map<String, List<PartitionInfo>> partitionsByTopic = consumer.listTopics();
            if (partitionsByTopic.containsKey(topic)) {
                List<TopicPartition> topicPartitions = partitionsByTopic.get(topic).stream()
                        .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                        .collect(Collectors.toList());
                if (!consumer.assignment().containsAll(topicPartitions)) {
                    consumer.assign(topicPartitions);
                    consumer.commitSync();
                }
                consumer.seekToEnd(topicPartitions);
                KafkaInterceptor.disable();
                consumer.partitionsFor(topic).stream()
                        .map(pi -> new TopicPartition(topic, pi.partition()))
                        .forEach(topicPartition -> {
                            long position = consumer.position(topicPartition);
                            log.debug("setting offset of {} topic to {}", topicPartition.topic(), position);
                            KafkaInterceptor.offsets().put(topicPartition, position);
                        });
                KafkaInterceptor.enable();
            }
        }));
    }

    @Override
    public long adjustedOffsetFor(TopicPartition tp) {
        return KafkaInterceptor.adjustedOffsetFor(tp);
    }

    @Override
    public long consumerSeekOffset(TopicPartition tp) {
        // The consumer proxy transparently handles offset adjustment,
        // so consumer seek operations use logical offset 0 (start of current test)
        return 0;
    }

    @Override
    public Map<TopicPartition, Long> pastOffsets() {
        return KafkaInterceptor.offsets();
    }

    @Override
    public void seekConsumerToTestStart(Consumer<?, ?> consumer, String topic) {
        List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                .collect(Collectors.toList());
        // The consumer proxy adjusts seekToBeginning to seek to the adjusted offset
        consumer.seekToBeginning(partitions);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ConsumerRecord<?, ?>> filterForCurrentTest(ConsumerRecords<?, ?> records) {
        // The consumer proxy already filters and rewrites offsets
        return StreamSupport.stream(((ConsumerRecords<Object, Object>) records).spliterator(), false)
                .map(record -> (ConsumerRecord<?, ?>) record)
                .collect(Collectors.toList());
    }

    // ===== Admin =====

    @Override
    public Map<String, Object> adminProperties() {
        return getAnyConsumerFactory().getConfigurationProperties();
    }

    private ConsumerFactory<?, ?> getAnyConsumerFactory() {
        return Stream.concat(
                Stream.concat(jsonConsumerFactories.stream(), avroConsumerFactories.stream()),
                avroJacksonConsumerFactories.stream()
        ).findFirst().orElseThrow(() -> new IllegalStateException("No ConsumerFactory available"));
    }

    // ===== "consumed from" lifecycle (Spring-only) =====

    public void beforePublishForConsumption(boolean successfully) {
        KafkaInterceptor.awaitForSuccessfullOnly = successfully;
    }

    public void afterPublishForConsumption(List<RecordMetadata> results) {
        results.parallelStream().forEach(metadata -> {
            awaitUntil(() -> KafkaInterceptor.isProcessed(metadata.toString()));
            KafkaInterceptor.removeProcessed(metadata.toString());
        });
        log.debug("processed {}", results);
    }

    public void afterPublishForConsumptionCleanup() {
        KafkaInterceptor.awaitForSuccessfullOnly = false;
    }

    // ===== Private helpers =====

    private <K, V> SendResult<K, V> blockingSend(KafkaTemplate<K, V> kafkaTemplate, ProducerRecord<K, V> producerRecord) {
        if (kafkaTemplate == null) {
            throw new IllegalStateException("""
                    Kafka message send failed. A KafkaTemplate is missing from your Spring application context.
                    To fix this, define a KafkaTemplate<KEY, VALUE> bean in your Spring configuration.
                    Use GenericRecord for Avro or String for JSON as KEY and VALUE types.""");
        }
        CompletableFuture<SendResult<K, V>> sendReturn = Methods.invokeUnchecked(
                kafkaTemplate, Methods.getMethod(KafkaTemplate.class, "send", ProducerRecord.class), producerRecord);
        return sendReturn.join();
    }
}
