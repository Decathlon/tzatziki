package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.kafka.KafkaConfigurationProperties;
import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import static com.decathlon.tzatziki.utils.Patterns.*;
import static org.assertj.core.api.Assertions.assertThat;

public class TestPlainKafkaConsumerSteps {

    private static final Map<String, List<Map<String, Object>>> CONSUMED_JSON_MESSAGES = new ConcurrentHashMap<>();

    private final ObjectSteps objects;

    public TestPlainKafkaConsumerSteps(ObjectSteps objects) {
        this.objects = objects;
    }

    @Before
    public void before() {
        CONSUMED_JSON_MESSAGES.clear();
    }

    @When(THAT + "the " + VARIABLE + " group id consumes the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic as json$")
    public void the_group_id_consumes_the_topic_as_json(String groupId, String topicValue) {
        String topic = objects.resolve(topicValue);
        CONSUMED_JSON_MESSAGES.put(consumptionKey(groupId, topic), consumeAvailableJsonMessages(groupId, topic));
    }

    @Then(THAT + "the " + VARIABLE + " group id has consumed (\\d+) json messages from the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic$")
    public void the_group_id_has_consumed_json_messages_from_the_topic(String groupId, int expectedCount, String topicValue) {
        assertThat(consumedMessages(groupId, objects.resolve(topicValue))).hasSize(expectedCount);
    }

    @Then(THAT + "the " + VARIABLE + " group id has consumed the following json messages from the " + VARIABLE_OR_TEMPLATE_PATTERN + " topic:$")
    public void the_group_id_has_consumed_the_following_json_messages_from_the_topic(String groupId, String topicValue, Object content) {
        assertThat(consumedMessages(groupId, objects.resolve(topicValue)))
                .isEqualTo(asListOfMaps(objects.resolve(content)));
    }

    private List<Map<String, Object>> consumeAvailableJsonMessages(String groupId, String topic) {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties(groupId, topic))) {
            consumer.subscribe(List.of(topic));
            List<Map<String, Object>> consumedMessages = new ArrayList<>();
            int consecutiveEmptyPolls = 0;
            while (consecutiveEmptyPolls < 5) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
                if (records.isEmpty()) {
                    consecutiveEmptyPolls++;
                    continue;
                }
                consecutiveEmptyPolls = 0;
                records.forEach(consumerRecord -> consumedMessages.add(Mapper.read(consumerRecord.value())));
                consumer.commitSync();
            }
            return consumedMessages;
        }
    }

    private Properties consumerProperties(String groupId, String topic) {
        Properties defaults = new Properties();
        defaults.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaConfigurationProperties.getBootstrapServers());
        defaults.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        defaults.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, KafkaConfigurationProperties.getConsumerAutoOffsetReset());
        defaults.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        defaults.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        defaults.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return KafkaConfigurationProperties.buildProperties(defaults, topic);
    }

    private List<Map<String, Object>> consumedMessages(String groupId, String topic) {
        return CONSUMED_JSON_MESSAGES.getOrDefault(consumptionKey(groupId, topic), List.of());
    }

    private String consumptionKey(String groupId, String topic) {
        return groupId + "@" + topic;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asListOfMaps(String content) {
        Object parsed = Mapper.read(content);
        if (parsed instanceof List<?>) {
            return (List<Map<String, Object>>) parsed;
        }
        return List.of((Map<String, Object>) parsed);
    }
}
