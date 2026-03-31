package com.decathlon.tzatziki.kafka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaConfigurationProperties} property prefix forwarding and customizer support.
 */
class KafkaConfigurationPropertiesTest {

    @AfterEach
    void cleanup() {
        KafkaConfigurationProperties.resetCustomizers();
        System.getProperties().stringPropertyNames().stream()
                .filter(k -> k.startsWith("tzatziki.kafka.common.") ||
                        k.startsWith("tzatziki.kafka.producer.") ||
                        k.startsWith("tzatziki.kafka.consumer.") ||
                        k.startsWith("tzatziki.kafka.admin.") ||
                        k.startsWith("tzatziki.kafka.avro-producer.") ||
                        k.startsWith("tzatziki.kafka.json-producer.") ||
                        k.startsWith("tzatziki.kafka.avro-consumer.") ||
                        k.startsWith("tzatziki.kafka.json-consumer."))
                .forEach(System::clearProperty);
    }

    @Test
    void buildProperties_returnsDefaults_whenNoExternalConfig() {
        Properties defaults = new Properties();
        defaults.put("bootstrap.servers", "localhost:9092");
        defaults.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        Properties result = KafkaConfigurationProperties.buildProperties(defaults, KafkaClientType.COMMON, KafkaClientType.PRODUCER);

        assertThat(result.getProperty("bootstrap.servers")).isEqualTo("localhost:9092");
        assertThat(result.getProperty("key.serializer")).isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
    }

    @Test
    void buildProperties_appliesCommonSystemProperties() {
        System.setProperty("tzatziki.kafka.common.security.protocol", "SSL");
        System.setProperty("tzatziki.kafka.common.ssl.keystore.location", "/path/to/keystore.p12");

        Properties defaults = new Properties();
        defaults.put("bootstrap.servers", "localhost:9092");

        Properties result = KafkaConfigurationProperties.buildProperties(defaults, KafkaClientType.COMMON);

        assertThat(result.getProperty("security.protocol")).isEqualTo("SSL");
        assertThat(result.getProperty("ssl.keystore.location")).isEqualTo("/path/to/keystore.p12");
        assertThat(result.getProperty("bootstrap.servers")).isEqualTo("localhost:9092");
    }

    @Test
    void buildProperties_typeSpecificOverridesCommon() {
        System.setProperty("tzatziki.kafka.common.security.protocol", "SSL");
        System.setProperty("tzatziki.kafka.producer.security.protocol", "SASL_SSL");

        Properties defaults = new Properties();
        Properties result = KafkaConfigurationProperties.buildProperties(defaults,
                KafkaClientType.COMMON, KafkaClientType.PRODUCER);

        assertThat(result.getProperty("security.protocol")).isEqualTo("SASL_SSL");
    }

    @Test
    void buildProperties_formatSpecificOverridesTypeSpecific() {
        System.setProperty("tzatziki.kafka.producer.batch.size", "1000");
        System.setProperty("tzatziki.kafka.avro-producer.batch.size", "2000");

        Properties defaults = new Properties();
        Properties result = KafkaConfigurationProperties.buildProperties(defaults,
                KafkaClientType.COMMON, KafkaClientType.PRODUCER, KafkaClientType.AVRO_PRODUCER);

        assertThat(result.getProperty("batch.size")).isEqualTo("2000");
    }

    @Test
    void buildProperties_systemPropertiesOverrideDefaults() {
        System.setProperty("tzatziki.kafka.common.bootstrap.servers", "remote-kafka:9093");

        Properties defaults = new Properties();
        defaults.put("bootstrap.servers", "localhost:9092");

        Properties result = KafkaConfigurationProperties.buildProperties(defaults, KafkaClientType.COMMON);

        assertThat(result.getProperty("bootstrap.servers")).isEqualTo("remote-kafka:9093");
    }

    @Test
    void customize_appliesCustomizerAfterSystemProperties() {
        System.setProperty("tzatziki.kafka.common.security.protocol", "SSL");

        KafkaConfigurationProperties.customize(KafkaClientType.COMMON, props ->
                props.put("security.protocol", "SASL_SSL"));

        Properties defaults = new Properties();
        Properties result = KafkaConfigurationProperties.buildProperties(defaults, KafkaClientType.COMMON);

        assertThat(result.getProperty("security.protocol")).isEqualTo("SASL_SSL");
    }

    @Test
    void customize_scopedCustomizerOnlyAffectsMatchingScope() {
        KafkaConfigurationProperties.customize(KafkaClientType.AVRO_PRODUCER, props ->
                props.put("schema.registry.basic.auth.user.info", "user:pass"));

        Properties defaults = new Properties();

        Properties avroResult = KafkaConfigurationProperties.buildProperties(defaults,
                KafkaClientType.COMMON, KafkaClientType.PRODUCER, KafkaClientType.AVRO_PRODUCER);
        assertThat(avroResult.getProperty("schema.registry.basic.auth.user.info")).isEqualTo("user:pass");

        Properties jsonResult = KafkaConfigurationProperties.buildProperties(defaults,
                KafkaClientType.COMMON, KafkaClientType.PRODUCER, KafkaClientType.JSON_PRODUCER);
        assertThat(jsonResult.getProperty("schema.registry.basic.auth.user.info")).isNull();
    }

    @Test
    void customize_multipleCustomizersApplyInOrder() {
        KafkaConfigurationProperties.customize(KafkaClientType.COMMON, props ->
                props.put("client.id", "first"));
        KafkaConfigurationProperties.customize(KafkaClientType.COMMON, props ->
                props.put("client.id", "second"));

        Properties defaults = new Properties();
        Properties result = KafkaConfigurationProperties.buildProperties(defaults, KafkaClientType.COMMON);

        assertThat(result.getProperty("client.id")).isEqualTo("second");
    }

    @Test
    void resetCustomizers_clearsAllRegisteredCustomizers() {
        KafkaConfigurationProperties.customize(KafkaClientType.COMMON, props ->
                props.put("custom.key", "custom.value"));

        KafkaConfigurationProperties.resetCustomizers();

        Properties defaults = new Properties();
        Properties result = KafkaConfigurationProperties.buildProperties(defaults, KafkaClientType.COMMON);

        assertThat(result.getProperty("custom.key")).isNull();
    }

    @Test
    void buildProperties_fullHierarchy_commonThenProducerThenAvroProducer() {
        System.setProperty("tzatziki.kafka.common.security.protocol", "SSL");
        System.setProperty("tzatziki.kafka.producer.acks", "all");
        System.setProperty("tzatziki.kafka.avro-producer.schema.registry.url", "http://custom-registry:8081");

        Properties defaults = new Properties();
        defaults.put("bootstrap.servers", "localhost:9092");
        defaults.put("schema.registry.url", "mock://test");

        Properties result = KafkaConfigurationProperties.buildProperties(defaults,
                KafkaClientType.COMMON, KafkaClientType.PRODUCER, KafkaClientType.AVRO_PRODUCER);

        assertThat(result.getProperty("bootstrap.servers")).isEqualTo("localhost:9092");
        assertThat(result.getProperty("security.protocol")).isEqualTo("SSL");
        assertThat(result.getProperty("acks")).isEqualTo("all");
        assertThat(result.getProperty("schema.registry.url")).isEqualTo("http://custom-registry:8081");
    }

    @Test
    void buildProperties_adminScope() {
        System.setProperty("tzatziki.kafka.common.security.protocol", "SSL");
        System.setProperty("tzatziki.kafka.admin.request.timeout.ms", "5000");

        Properties defaults = new Properties();
        defaults.put("bootstrap.servers", "localhost:9092");

        Properties result = KafkaConfigurationProperties.buildProperties(defaults,
                KafkaClientType.COMMON, KafkaClientType.ADMIN);

        assertThat(result.getProperty("security.protocol")).isEqualTo("SSL");
        assertThat(result.getProperty("request.timeout.ms")).isEqualTo("5000");
        assertThat(result.getProperty("bootstrap.servers")).isEqualTo("localhost:9092");
    }

    @Test
    void legacyAccessors_stillWorkUnchanged() {
        assertThat(KafkaConfigurationProperties.getConsumerAutoOffsetReset()).isEqualTo("earliest");
        assertThat(KafkaConfigurationProperties.getConsumerGroupId()).isEqualTo("tzatziki-kafka-test");
        assertThat(KafkaConfigurationProperties.getSchemaRegistryUrl()).isEqualTo("mock://tzatziki-kafka-steps-scope");
    }
}
