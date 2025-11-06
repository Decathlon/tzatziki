package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.steps.KafkaSteps;
import com.decathlon.tzatziki.utils.Fields;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jetbrains.annotations.NotNull;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;

import static com.decathlon.tzatziki.utils.Asserts.awaitUntil;
import static java.util.Collections.synchronizedMap;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@SuppressWarnings("unchecked")
@Aspect
@Component
@AllArgsConstructor
@Slf4j
public class KafkaInterceptor {

    private static final Set<String> PROCESSED = new LinkedHashSet<>();
    private static final Set<String> PROCESSING = new LinkedHashSet<>();
    private static final Map<TopicPartition, Long> PAST_OFFSETS = synchronizedMap(new LinkedHashMap<>());
    private static final Map<TopicPartition, Long> CURRENT_OFFSETS = synchronizedMap(new LinkedHashMap<>());
    public static boolean awaitForSuccessfullOnly;

    private static boolean enabled = true;

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    @Around(value = "@annotation(org.springframework.kafka.annotation.KafkaListener) || @annotation(org.springframework.kafka.annotation.KafkaListeners)")
    public Object receiveMessages(ProceedingJoinPoint joinPoint) throws Throwable {
        Throwable throwable = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            throwable = t;
            throw t;
        } finally {
            if (throwable == null || !awaitForSuccessfullOnly) {
                PROCESSED.addAll(PROCESSING);
                PROCESSING.clear();
            }
        }
    }

    @Around("@annotation(org.springframework.context.annotation.Bean) && !within(is(FinalType))")
    public Object beanCreation(ProceedingJoinPoint joinPoint) throws Throwable {
        Object bean = joinPoint.proceed();
        if (bean instanceof DefaultKafkaConsumerFactory consumerFactory) {
            return proxyOfConsumerFactory(consumerFactory);
        }
        return bean;
    }

    public static Map<TopicPartition, Long> offsets() {
        return PAST_OFFSETS;
    }

    private ConsumerFactory<?, ?> proxyOfConsumerFactory(DefaultKafkaConsumerFactory<?, ?> consumerFactory) {
        return (ConsumerFactory<?, ?>) Proxy.newProxyInstance(ConsumerFactory.class.getClassLoader(), new Class[]{ConsumerFactory.class}, (proxy, method, args) -> {
            Object result = method.invoke(consumerFactory, args);
            if ("createConsumer".equals(method.getName())) {
                return createConsumerProxy((Consumer<?, ?>) result);
            }
            return result;
        });
    }

    private Object createConsumerProxy(Consumer<?, ?> consumer) {
        return Proxy.newProxyInstance(Consumer.class.getClassLoader(), new Class[]{Consumer.class}, (proxy, method, args) -> {
            try {
                return switch (method.getName()) {
                    case "poll" -> {
                        ConsumerRecords<String, ?> consumerRecords = (ConsumerRecords<String, ?>) method.invoke(consumer, args);
                        consumer.subscription().stream()
                                .filter(KafkaSteps.semaphoreByTopic::containsKey)
                                .forEach(topic -> KafkaSteps.semaphoreByTopic.remove(topic).release());
                        if (consumerRecords.count() == 0) {
                            yield consumerRecords;
                        }

                        for (ConsumerRecord<String, ?> record : consumerRecords) {
                            PROCESSING.add("%s-%s@%s".formatted(record.topic(), record.partition(), record.offset()));
                            CURRENT_OFFSETS.compute(topicPartitionOf(record),
                                    (t, current) -> Math.max(ofNullable(current).orElse(0L), record.offset() + 1));
                            adjustedOffsetFor(topicPartitionOf(record));
                        }

                        yield new ConsumerRecords<>(rewriteOffsets(Fields.getValue(consumerRecords, "records")));
                    }
                    case "seek" -> {
                        TopicPartition topicPartition = (TopicPartition) args[0];
                        if (args[1] instanceof Long offset) {
                            offset += adjustedOffsetFor(topicPartition);
                            consumer.seek(topicPartition, offset);
                        } else {
                            OffsetAndMetadata offsetAndMetadata = (OffsetAndMetadata) args[1];
                            offsetAndMetadata = new OffsetAndMetadata(
                                    adjustedOffsetFor(topicPartition) + offsetAndMetadata.offset(),
                                    offsetAndMetadata.metadata());
                            consumer.seek(topicPartition, offsetAndMetadata);
                        }
                        yield null;
                    }
                    case "position" -> {
                        TopicPartition topicPartition = (TopicPartition) args[0];
                        long offset = adjustedOffsetFor(topicPartition);
                        if (args.length == 2) {
                            yield consumer.position(topicPartition, (Duration) args[1]) - offset;
                        }
                        yield consumer.position(topicPartition) - offset;
                    }
                    case "endOffsets" -> {
                        Map<TopicPartition, Long> endOffsets = (Map<TopicPartition, Long>) method.invoke(consumer, args);
                        yield endOffsets.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> e.getValue() - adjustedOffsetFor(e.getKey())));
                    }
                    case "seekToBeginning" -> {
                        Collection<TopicPartition> topicPartitions = (Collection<TopicPartition>) args[0];
                        topicPartitions.forEach(topicPartition -> consumer.seek(topicPartition, adjustedOffsetFor(topicPartition)));
                        yield null;
                    }
                    default -> method.invoke(consumer, args);
                };
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        });
    }

    private <E> Map<TopicPartition, List<ConsumerRecord<E, E>>> rewriteOffsets(Map<TopicPartition, List<ConsumerRecord<E, E>>> records) {
        // rewriting the offsets in the messages
        records = records.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, e -> e.getValue().stream()
                        .map(record -> new ConsumerRecord<>(
                                record.topic(),
                                record.partition(),
                                record.offset() - PAST_OFFSETS.getOrDefault(topicPartitionOf(record), 0L),
                                record.timestamp(),
                                record.timestampType(),
                                record.serializedKeySize(),
                                record.serializedValueSize(),
                                record.key(),
                                record.value(),
                                record.headers(),
                                record.leaderEpoch()))
                        .filter(record -> record.offset() >= 0)
                        .collect(toList())));
        return records;
    }

    @NotNull
    private TopicPartition topicPartitionOf(ConsumerRecord<?, ?> record) {
        return new TopicPartition(record.topic(), record.partition());
    }

    public static long adjustedOffsetFor(TopicPartition topicPartition) {
        long offset = enabled ? PAST_OFFSETS.getOrDefault(topicPartition, 0L) : 0L;
        if (offset > 0) {
            log.debug("adjusted offset for %s is %s".formatted(topicPartition, offset));
        }
        return offset;
    }

    public static SendResult<?, ?> waitUntilProcessed(SendResult<?, ?> result) {
        awaitUntil(() -> PROCESSED.contains(result.getRecordMetadata().toString()));
        PROCESSED.remove(result.getRecordMetadata().toString());
        return result;
    }

    public static void before() {
        PAST_OFFSETS.putAll(CURRENT_OFFSETS);
        CURRENT_OFFSETS.clear();
        PROCESSING.clear();
        PROCESSED.clear();
    }
}
