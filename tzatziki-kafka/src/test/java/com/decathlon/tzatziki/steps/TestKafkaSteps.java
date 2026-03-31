package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.kafka.KafkaConfigurationProperties;
import io.cucumber.java.BeforeAll;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Test lifecycle setup for tzatziki-kafka module tests.
 * Starts a TestContainers Kafka broker and configures system properties.
 */
public class TestKafkaSteps {

    private static final KafkaContainer kafka = new KafkaContainer("apache/kafka-native:latest");

    @BeforeAll
    public static void beforeAll() {
        if (!kafka.isRunning()) {
            kafka.start();
        }
        System.setProperty(KafkaConfigurationProperties.BOOTSTRAP_SERVERS, kafka.getBootstrapServers());
        KafkaSteps.autoSeekTopics("json-output");
    }
}
