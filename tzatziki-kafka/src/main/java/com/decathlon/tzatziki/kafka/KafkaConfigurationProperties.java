package com.decathlon.tzatziki.kafka;

/**
 * Configuration properties for tzatziki-kafka, read from system properties.
 * <p>
 * Set via {@code -D} flags, e.g.: {@code -Dtzatziki.kafka.bootstrap-servers=localhost:9092}
 */
public class KafkaConfigurationProperties {

    private KafkaConfigurationProperties() {
    }

    public static final String BOOTSTRAP_SERVERS = "tzatziki.kafka.bootstrap-servers";
    public static final String SCHEMA_REGISTRY_URL = "tzatziki.kafka.schema-registry-url";
    public static final String CONSUMER_GROUP_ID = "tzatziki.kafka.consumer.group-id";
    public static final String CONSUMER_AUTO_OFFSET_RESET = "tzatziki.kafka.consumer.auto-offset-reset";

    public static String getBootstrapServers() {
        return System.getProperty(BOOTSTRAP_SERVERS, "localhost:9092");
    }

    public static String getSchemaRegistryUrl() {
        return System.getProperty(SCHEMA_REGISTRY_URL, "mock://tzatziki-kafka-steps-scope");
    }

    public static String getConsumerGroupId() {
        return System.getProperty(CONSUMER_GROUP_ID, "tzatziki-kafka-test");
    }

    public static String getConsumerAutoOffsetReset() {
        return System.getProperty(CONSUMER_AUTO_OFFSET_RESET, "earliest");
    }
}
