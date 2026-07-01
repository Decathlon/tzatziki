package com.decathlon.tzatziki.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.synchronizedMap;

/**
 * Manages Kafka offsets between test scenarios to provide deterministic behavior.
 * <p>
 * This replaces the Spring AOP-based {@code KafkaInterceptor} from tzatziki-spring-kafka.
 * Instead of proxying consumer factories via AOP, it tracks offsets explicitly and
 * provides methods to seek test consumers to the correct position.
 */
@Slf4j
public class KafkaOffsetManager {

    private static final Map<TopicPartition, Long> PAST_OFFSETS = synchronizedMap(new LinkedHashMap<>());

    private static boolean enabled = true;

    private KafkaOffsetManager() {
    }

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the adjusted offset for a given topic-partition.
     * This is the baseline offset from which the current test's messages start.
     */
    public static long adjustedOffsetFor(TopicPartition topicPartition) {
        long offset = enabled ? PAST_OFFSETS.getOrDefault(topicPartition, 0L) : 0L;
        if (offset > 0) {
            log.debug("adjusted offset for {} is {}", topicPartition, offset);
        }
        return offset;
    }

    /**
     * Seeks the consumer to the start of the current test's messages for the given topic.
     * If offset manager is disabled, seeks to beginning.
     */
    public static void seekToTestStart(Consumer<?, ?> consumer, String topic) {
        List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                .map(info -> new TopicPartition(info.topic(), info.partition()))
                .toList();
        if (!consumer.assignment().containsAll(partitions)) {
            consumer.assign(partitions);
        }
        for (TopicPartition tp : partitions) {
            long startOffset = adjustedOffsetFor(tp);
            consumer.seek(tp, startOffset);
        }
    }

    /**
     * Seeks the consumer to the end of all partitions for the given topic
     * and records the positions as PAST_OFFSETS (useful for auto-seek topics).
     */
    public static void seekToEndAndRecord(Consumer<?, ?> consumer, String topic) {
        List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic);
        if (partitionInfos == null || partitionInfos.isEmpty()) {
            return;
        }
        List<TopicPartition> partitions = partitionInfos.stream()
                .map(info -> new TopicPartition(info.topic(), info.partition()))
                .toList();
        if (!consumer.assignment().containsAll(partitions)) {
            consumer.assign(partitions);
            consumer.commitSync();
        }
        consumer.seekToEnd(partitions);
        for (TopicPartition tp : partitions) {
            long position = consumer.position(tp);
            log.debug("setting offset of {} topic to {}", tp.topic(), position);
            PAST_OFFSETS.put(tp, position);
        }
    }

    /**
     * Resets all tracked offsets. Use with caution.
     */
    public static void reset() {
        PAST_OFFSETS.clear();
    }
}
