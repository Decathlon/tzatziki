package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.kafka.KafkaInterceptor;
import com.decathlon.tzatziki.kafka.SpringKafkaBackend;
import com.decathlon.tzatziki.utils.Guard;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import jakarta.annotation.Nullable;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.Assertions;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.util.*;
import java.util.concurrent.Semaphore;

import static com.decathlon.tzatziki.utils.Asserts.awaitUntil;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;

/**
 * Spring-specific Kafka step definitions.
 * <p>
 * Registers {@link SpringKafkaBackend} on {@link KafkaSteps} and provides
 * additional steps that only make sense with Spring Kafka (message consumption
 * with listener wait, interceptor control, poll tracking, deprecated steps).
 */
@Slf4j
@SuppressWarnings({
        "java:S100",
        "java:S107"
})
public class SpringKafkaSteps {

    public static final String SCHEMA_REGISTRY_URL = "mock://tzatziki-kafka-steps-scope";

    private static final EmbeddedKafkaBroker embeddedKafka = new EmbeddedKafkaKraftBroker(1, 1);
    private static final Set<String> checkedTopics = new LinkedHashSet<>();
    private static final Map<String, Semaphore> semaphoreByTopic = new LinkedHashMap<>();
    private static boolean isStarted;

    private final KafkaSteps kafkaSteps;
    private final ObjectSteps objects;
    private final SpringKafkaBackend springKafkaBackend;

    public SpringKafkaSteps(
            KafkaSteps kafkaSteps,
            ObjectSteps objects,
            @Nullable KafkaTemplate<GenericRecord, GenericRecord> avroKeyMessageKafkaTemplate,
            @Nullable KafkaTemplate<String, GenericRecord> avroKafkaTemplate,
            @Nullable KafkaTemplate<String, String> jsonKafkaTemplate,
            Optional<List<ConsumerFactory<Object, Object>>> avroJacksonConsumerFactories,
            Optional<List<ConsumerFactory<String, GenericRecord>>> avroConsumerFactories,
            Optional<List<ConsumerFactory<String, String>>> jsonConsumerFactories) {
        this.kafkaSteps = kafkaSteps;
        this.objects = objects;
        this.springKafkaBackend = new SpringKafkaBackend(
                avroKeyMessageKafkaTemplate,
                avroKafkaTemplate,
                jsonKafkaTemplate,
                avroJacksonConsumerFactories.orElse(new ArrayList<>()),
                avroConsumerFactories.orElse(new ArrayList<>()),
                jsonConsumerFactories.orElse(new ArrayList<>()));
    }

    // ========== Embedded Kafka lifecycle ==========

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

    public static String bootstrapServers() {
        return embeddedKafka.getBrokersAsString();
    }

    public static String schemaRegistryUrl() {
        return SCHEMA_REGISTRY_URL;
    }

    /**
     * Disables the initial consumer-group-member wait for the Spring-only
     * "consumed from" step on the given topic.
     */
    public static void doNotWaitForMembersOn(String topic) {
        checkedTopics.add(topic);
    }

    /**
     * Returns whether the Spring-only "consumed from" step already skipped or
     * completed its initial member check for the given topic.
     */
    public static boolean isTopicChecked(String topic) {
        return checkedTopics.contains(topic);
    }

    public static void registerSemaphore(String topic, Semaphore semaphore) {
        semaphoreByTopic.put(topic, semaphore);
    }

    public static boolean hasSemaphore(String topic) {
        return semaphoreByTopic.containsKey(topic);
    }

    public static Semaphore removeSemaphore(String topic) {
        return semaphoreByTopic.remove(topic);
    }

    // ========== Backend registration ==========

    @Before(order = Integer.MIN_VALUE)
    public void registerBackend() {
        KafkaSteps.setBackend(springKafkaBackend);
    }

    // ========== Spring-only steps ==========

    /**
     * Publishes a message and waits for the application's {@code @KafkaListener} to process it.
     * This step only makes sense with Spring Kafka — in plain Kafka there is no listener to wait for.
     */
    @SneakyThrows
    @When(THAT + GUARD + A + KafkaSteps.RECORD + "( with key " + VARIABLE + ")? (?:is|are)? (successfully )?consumed from the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic:$")
    public void a_message_is_consumed_from_a_topic(Guard guard, String name, String key, boolean successfully, String topicValue, Object content) {
        guard.in(objects, () -> {
            String topic = objects.resolve(topicValue);
            if (!isTopicChecked(topic)) {
                try (Admin admin = Admin.create(springKafkaBackend.adminProperties())) {
                    awaitUntil(() -> {
                        List<String> groupIds = admin.listGroups().all().get().stream().map(GroupListing::groupId).toList();
                        Map<String, KafkaFuture<ConsumerGroupDescription>> groupDescriptions = admin.describeConsumerGroups(groupIds).describedGroups();
                        return groupIds.stream()
                                .anyMatch(groupId -> unchecked(() -> groupDescriptions.get(groupId).get())
                                        .members().stream()
                                        .anyMatch(member -> member.assignment().topicPartitions().stream()
                                                .anyMatch(topicPartition -> topicPartition.topic().equals(topic))));
                    });
                    doNotWaitForMembersOn(topic);
                }
            }
            SpringKafkaBackend.beforePublishForConsumption(successfully);
            try {
                List<RecordMetadata> results = kafkaSteps.publishMessage(name, topic, content, key);
                springKafkaBackend.afterPublishForConsumption(results);
            } finally {
                SpringKafkaBackend.afterPublishForConsumptionCleanup();
            }
        });
    }

    @When(THAT + GUARD + "the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic was just polled$")
    public void topic_was_just_polled(Guard guard, String topic) {
        guard.in(objects, () -> {
            Semaphore semaphore = new Semaphore(0);
            registerSemaphore(objects.resolve(topic), semaphore);
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Assertions.fail(e);
            }
        });
    }

    /**
     * @deprecated Use {@code we disable kafka offset manager} step instead.
     */
    @Deprecated(forRemoval = true)
    @Given(THAT + "we disable kafka interceptor$")
    public void disable_kafka_interceptor() {
        KafkaInterceptor.disable();
    }

    /** @deprecated Use {@code we enable kafka offset manager} step instead. */
    @Deprecated(forRemoval = true)
    @Given(THAT + "we enable kafka interceptor$")
    public void enable_kafka_interceptor() {
        KafkaInterceptor.enable();
    }

    // ========== Deprecated steps ==========

    /** @deprecated Use {@code consumed from} step instead. */
    @Deprecated(forRemoval = true)
    @When(THAT + GUARD + A + KafkaSteps.RECORD + " (?:is|are)? received on the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic:$")
    public void a_message_is_received_on_a_topic(Guard guard, String name, String topic, Object content) {
        a_message_is_consumed_from_a_topic(guard, name, null, false, objects.resolve(topic), content);
    }

    /** @deprecated Use {@code consumed from} step instead. */
    @Deprecated(forRemoval = true)
    @When(THAT + GUARD + A_USER + "receives? " + A + VARIABLE + " on the topic " + VARIABLE_OR_TEMPLATE_PATTERN + ":$")
    public void we_receive_a_message_on_a_topic(Guard guard, String name, String topic, Object content) {
        a_message_is_consumed_from_a_topic(guard, name, null, false, objects.resolve(topic), content);
    }
}
