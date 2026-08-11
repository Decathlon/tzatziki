package com.decathlon.tzatziki.kafka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link KafkaConfigurationProperties}: system property forwarding,
 * global/topic customizers, and cluster abstraction.
 */
class KafkaConfigurationPropertiesTest {

    @AfterEach
    void cleanup() {
        KafkaConfigurationProperties.resetCustomizers();
        System.getProperties().stringPropertyNames().stream()
                .filter(k -> k.startsWith("tzatziki.kafka.") && !k.equals("tzatziki.kafka.bootstrap-servers")
                        && !k.equals("tzatziki.kafka.schema-registry-url"))
                .forEach(System::clearProperty);
    }

    // ===== Defaults =====

    @Test
    void buildProperties_returnsDefaults_whenNoExternalConfig() {
        Properties defaults = new Properties();
        defaults.put("bootstrap.servers", "localhost:9092");
        defaults.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        Properties result = KafkaConfigurationProperties.buildProperties(defaults);

        assertThat(result.getProperty("bootstrap.servers")).isEqualTo("localhost:9092");
        assertThat(result.getProperty("key.serializer")).isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
    }

    // ===== Global system properties =====

    @Test
    void buildProperties_appliesGlobalSystemProperties() {
        System.setProperty("tzatziki.kafka.security.protocol", "SSL");
        System.setProperty("tzatziki.kafka.ssl.keystore.location", "/path/to/keystore.p12");

        Properties defaults = new Properties();
        defaults.put("bootstrap.servers", "localhost:9092");

        Properties result = KafkaConfigurationProperties.buildProperties(defaults);

        assertThat(result.getProperty("security.protocol")).isEqualTo("SSL");
        assertThat(result.getProperty("ssl.keystore.location")).isEqualTo("/path/to/keystore.p12");
        assertThat(result.getProperty("bootstrap.servers")).isEqualTo("localhost:9092");
    }

    @Test
    void buildProperties_systemPropertiesOverrideDefaults() {
        System.setProperty("tzatziki.kafka.bootstrap.servers", "remote-kafka:9093");

        Properties defaults = new Properties();
        defaults.put("bootstrap.servers", "localhost:9092");

        Properties result = KafkaConfigurationProperties.buildProperties(defaults);

        assertThat(result.getProperty("bootstrap.servers")).isEqualTo("remote-kafka:9093");
    }

    // ===== Global customizers =====

    @Test
    void customize_global_appliesAfterSystemProperties() {
        System.setProperty("tzatziki.kafka.security.protocol", "SSL");

        KafkaConfigurationProperties.customize(props ->
                props.put("security.protocol", "SASL_SSL"));

        Properties result = KafkaConfigurationProperties.buildProperties(new Properties());

        assertThat(result.getProperty("security.protocol")).isEqualTo("SASL_SSL");
    }

    @Test
    void customize_global_multipleApplyInOrder() {
        KafkaConfigurationProperties.customize(props ->
                props.put("client.id", "first"));
        KafkaConfigurationProperties.customize(props ->
                props.put("client.id", "second"));

        Properties result = KafkaConfigurationProperties.buildProperties(new Properties());

        assertThat(result.getProperty("client.id")).isEqualTo("second");
    }

    // ===== Topic-specific system properties =====

    @Test
    void buildProperties_topicSystemPropertiesOverrideGlobal() {
        System.setProperty("tzatziki.kafka.security.protocol", "SSL");
        System.setProperty("tzatziki.kafka.topic.orders.security.protocol", "SASL_SSL");

        Properties result = KafkaConfigurationProperties.buildProperties(new Properties(), "orders");

        assertThat(result.getProperty("security.protocol")).isEqualTo("SASL_SSL");
    }

    @Test
    void buildProperties_topicSystemPropertiesDoNotAffectOtherTopics() {
        System.setProperty("tzatziki.kafka.topic.orders.acks", "all");

        Properties ordersResult = KafkaConfigurationProperties.buildProperties(new Properties(), "orders");
        assertThat(ordersResult.getProperty("acks")).isEqualTo("all");

        Properties usersResult = KafkaConfigurationProperties.buildProperties(new Properties(), "users");
        assertThat(usersResult.getProperty("acks")).isNull();
    }

    @Test
    void buildProperties_nullTopicSkipsTopicLayer() {
        System.setProperty("tzatziki.kafka.topic.orders.security.protocol", "SSL");

        Properties result = KafkaConfigurationProperties.buildProperties(new Properties(), null);

        assertThat(result.getProperty("security.protocol")).isNull();
    }

    // ===== Topic-specific customizers =====

    @Test
    void customize_topic_overridesGlobal() {
        KafkaConfigurationProperties.customize(props ->
                props.put("security.protocol", "SSL"));
        KafkaConfigurationProperties.customize("orders", props ->
                props.put("security.protocol", "SASL_SSL"));

        Properties result = KafkaConfigurationProperties.buildProperties(new Properties(), "orders");

        assertThat(result.getProperty("security.protocol")).isEqualTo("SASL_SSL");
    }

    @Test
    void customize_topic_doesNotAffectOtherTopics() {
        KafkaConfigurationProperties.customize("orders", props ->
                props.put("client.id", "orders-client"));

        Properties ordersResult = KafkaConfigurationProperties.buildProperties(new Properties(), "orders");
        assertThat(ordersResult.getProperty("client.id")).isEqualTo("orders-client");

        Properties usersResult = KafkaConfigurationProperties.buildProperties(new Properties(), "users");
        assertThat(usersResult.getProperty("client.id")).isNull();
    }

    // ===== Cluster abstraction =====

    @Test
    void defineCluster_appliesPropertiesToMappedTopics() {
        KafkaConfigurationProperties.defineCluster("prod", props -> {
            props.put("bootstrap.servers", "kafka-prod:9093");
            props.put("security.protocol", "SSL");
        });
        KafkaConfigurationProperties.mapTopicToCluster("orders", "prod");
        KafkaConfigurationProperties.mapTopicToCluster("payments", "prod");

        Properties ordersResult = KafkaConfigurationProperties.buildProperties(new Properties(), "orders");
        assertThat(ordersResult.getProperty("bootstrap.servers")).isEqualTo("kafka-prod:9093");
        assertThat(ordersResult.getProperty("security.protocol")).isEqualTo("SSL");

        Properties paymentsResult = KafkaConfigurationProperties.buildProperties(new Properties(), "payments");
        assertThat(paymentsResult.getProperty("bootstrap.servers")).isEqualTo("kafka-prod:9093");
        assertThat(paymentsResult.getProperty("security.protocol")).isEqualTo("SSL");
    }

    @Test
    void defineCluster_doesNotAffectUnmappedTopics() {
        KafkaConfigurationProperties.defineCluster("prod", props ->
                props.put("bootstrap.servers", "kafka-prod:9093"));
        KafkaConfigurationProperties.mapTopicToCluster("orders", "prod");

        Properties unmappedResult = KafkaConfigurationProperties.buildProperties(new Properties(), "users");
        assertThat(unmappedResult.getProperty("bootstrap.servers")).isNull();
    }

    @Test
    void cluster_overridesGlobalCustomizer() {
        KafkaConfigurationProperties.customize(props ->
                props.put("bootstrap.servers", "global:9092"));
        KafkaConfigurationProperties.defineCluster("prod", props ->
                props.put("bootstrap.servers", "cluster:9093"));
        KafkaConfigurationProperties.mapTopicToCluster("orders", "prod");

        Properties result = KafkaConfigurationProperties.buildProperties(new Properties(), "orders");
        assertThat(result.getProperty("bootstrap.servers")).isEqualTo("cluster:9093");
    }

    @Test
    void topicCustomizer_overridesCluster() {
        KafkaConfigurationProperties.defineCluster("prod", props ->
                props.put("security.protocol", "SSL"));
        KafkaConfigurationProperties.mapTopicToCluster("orders", "prod");
        KafkaConfigurationProperties.customize("orders", props ->
                props.put("security.protocol", "PLAINTEXT"));

        Properties result = KafkaConfigurationProperties.buildProperties(new Properties(), "orders");
        assertThat(result.getProperty("security.protocol")).isEqualTo("PLAINTEXT");
    }

    @Test
    void topicSystemProperty_overridesCluster() {
        KafkaConfigurationProperties.defineCluster("prod", props ->
                props.put("security.protocol", "SSL"));
        KafkaConfigurationProperties.mapTopicToCluster("orders", "prod");
        System.setProperty("tzatziki.kafka.topic.orders.security.protocol", "SASL_SSL");

        Properties result = KafkaConfigurationProperties.buildProperties(new Properties(), "orders");
        assertThat(result.getProperty("security.protocol")).isEqualTo("SASL_SSL");
    }

    @Test
    void multipleClusters_isolatedCorrectly() {
        KafkaConfigurationProperties.defineCluster("cluster-a", props ->
                props.put("bootstrap.servers", "kafka-a:9092"));
        KafkaConfigurationProperties.defineCluster("cluster-b", props ->
                props.put("bootstrap.servers", "kafka-b:9093"));
        KafkaConfigurationProperties.mapTopicToCluster("topic-a", "cluster-a");
        KafkaConfigurationProperties.mapTopicToCluster("topic-b", "cluster-b");

        Properties resultA = KafkaConfigurationProperties.buildProperties(new Properties(), "topic-a");
        assertThat(resultA.getProperty("bootstrap.servers")).isEqualTo("kafka-a:9092");

        Properties resultB = KafkaConfigurationProperties.buildProperties(new Properties(), "topic-b");
        assertThat(resultB.getProperty("bootstrap.servers")).isEqualTo("kafka-b:9093");
    }

    @Test
    void mapTopicToCluster_unknownCluster_throws() {
        assertThatThrownBy(() ->
                KafkaConfigurationProperties.mapTopicToCluster("orders", "nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    // ===== Full hierarchy =====

    @Test
    void buildProperties_fullHierarchyWithClusterAndTopic() {
        System.setProperty("tzatziki.kafka.security.protocol", "PLAINTEXT");

        KafkaConfigurationProperties.customize(props ->
                props.put("client.id", "global"));
        KafkaConfigurationProperties.defineCluster("prod", props -> {
            props.put("bootstrap.servers", "kafka-prod:9093");
            props.put("security.protocol", "SSL");
        });
        KafkaConfigurationProperties.mapTopicToCluster("orders", "prod");
        System.setProperty("tzatziki.kafka.topic.orders.client.id", "orders-topic");
        KafkaConfigurationProperties.customize("orders", props ->
                props.put("acks", "all"));

        Properties defaults = new Properties();
        defaults.put("bootstrap.servers", "localhost:9092");

        Properties result = KafkaConfigurationProperties.buildProperties(defaults, "orders");

        // cluster overrides global system property and defaults
        assertThat(result.getProperty("bootstrap.servers")).isEqualTo("kafka-prod:9093");
        assertThat(result.getProperty("security.protocol")).isEqualTo("SSL");
        // topic system property overrides global customizer
        assertThat(result.getProperty("client.id")).isEqualTo("orders-topic");
        // topic customizer applied last
        assertThat(result.getProperty("acks")).isEqualTo("all");
    }

    // ===== Reset =====

    @Test
    void resetCustomizers_clearsEverything() {
        KafkaConfigurationProperties.customize(props ->
                props.put("global.key", "value"));
        KafkaConfigurationProperties.defineCluster("prod", props ->
                props.put("cluster.key", "value"));
        KafkaConfigurationProperties.mapTopicToCluster("orders", "prod");
        KafkaConfigurationProperties.customize("orders", props ->
                props.put("topic.key", "value"));

        KafkaConfigurationProperties.resetCustomizers();

        Properties result = KafkaConfigurationProperties.buildProperties(new Properties(), "orders");

        assertThat(result.getProperty("global.key")).isNull();
        assertThat(result.getProperty("cluster.key")).isNull();
        assertThat(result.getProperty("topic.key")).isNull();
    }

    // ===== Legacy accessors =====

    @Test
    void legacyAccessors_stillWorkUnchanged() {
        assertThat(KafkaConfigurationProperties.getConsumerAutoOffsetReset()).isEqualTo("earliest");
        assertThat(KafkaConfigurationProperties.getConsumerGroupId()).isEqualTo("tzatziki-kafka-test");
        assertThat(KafkaConfigurationProperties.getSchemaRegistryUrl()).isEqualTo("mock://tzatziki-kafka-steps-scope");
    }
}
