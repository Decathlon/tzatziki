package com.decathlon.tzatziki.kafka;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Configuration properties for tzatziki-kafka, read from system properties and/or programmatic customizers.
 * <p>
 * <b>Property prefix forwarding:</b> Any system property matching {@code tzatziki.kafka.<scope>.<kafka-property>}
 * is forwarded to the corresponding Kafka client configuration. Scopes are layered (later overrides earlier):
 * <ol>
 *   <li>{@code tzatziki.kafka.common.*} → all clients</li>
 *   <li>{@code tzatziki.kafka.producer.*} / {@code consumer.*} / {@code admin.*} → type-specific</li>
 *   <li>{@code tzatziki.kafka.avro-producer.*} / {@code json-producer.*} / {@code avro-consumer.*} / {@code json-consumer.*} → format-specific</li>
 *   <li>{@code tzatziki.kafka.topic.<topic-name>.<scope>.*} → topic-specific (highest priority)</li>
 * </ol>
 * <p>
 * <b>Programmatic customizers:</b> Register a {@link Consumer Consumer&lt;Properties&gt;} per {@link KafkaClientType}
 * via {@link #customize(KafkaClientType, Consumer)} (global) or {@link #customize(String, KafkaClientType, Consumer)} (per-topic).
 * Customizers are applied after system properties.
 * <p>
 * Set via {@code -D} flags, e.g.: {@code -Dtzatziki.kafka.topic.my-topic.producer.security.protocol=SSL}
 */
public class KafkaConfigurationProperties {

    private KafkaConfigurationProperties() {
    }

    private static final String TOPIC_PREFIX = "tzatziki.kafka.topic.";

    // ===== Legacy property keys (backward compatible) =====

    public static final String BOOTSTRAP_SERVERS = "tzatziki.kafka.bootstrap-servers";
    public static final String SCHEMA_REGISTRY_URL = "tzatziki.kafka.schema-registry-url";
    public static final String CONSUMER_GROUP_ID = "tzatziki.kafka.consumer.group-id";
    public static final String CONSUMER_AUTO_OFFSET_RESET = "tzatziki.kafka.consumer.auto-offset-reset";

    // ===== Customizer registry =====

    private static final Map<KafkaClientType, List<Consumer<Properties>>> globalCustomizers = new ConcurrentHashMap<>();
    private static final Map<String, Map<KafkaClientType, List<Consumer<Properties>>>> topicCustomizers = new ConcurrentHashMap<>();

    /**
     * Register a global programmatic customizer for a specific client type.
     * Customizers are applied after system property prefix forwarding, allowing final overrides.
     */
    public static void customize(KafkaClientType scope, Consumer<Properties> customizer) {
        globalCustomizers.computeIfAbsent(scope, k -> new ArrayList<>()).add(customizer);
    }

    /**
     * Register a topic-specific programmatic customizer.
     * Topic customizers are applied after global customizers, giving them the highest priority.
     * <p>
     * Example:
     * <pre>{@code
     * KafkaConfigurationProperties.customize("orders", KafkaClientType.PRODUCER, props -> {
     *     props.put("bootstrap.servers", "orders-cluster:9092");
     * });
     * }</pre>
     */
    public static void customize(String topic, KafkaClientType scope, Consumer<Properties> customizer) {
        topicCustomizers
                .computeIfAbsent(topic, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(scope, k -> new ArrayList<>())
                .add(customizer);
    }

    /**
     * Reset all registered customizers (global and topic-specific). Call in test teardown if needed.
     */
    public static void resetCustomizers() {
        globalCustomizers.clear();
        topicCustomizers.clear();
    }

    // ===== Legacy accessors (backward compatible) =====

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

    // ===== Property building =====

    /**
     * Builds properties without topic-specific overrides (global scopes only).
     *
     * @param defaults  baseline properties (serializers, bootstrap servers, etc.)
     * @param scopes    the scopes to apply, in order of increasing priority
     * @return merged properties ready for Kafka client creation
     */
    public static Properties buildProperties(Properties defaults, KafkaClientType... scopes) {
        return buildProperties(defaults, null, scopes);
    }

    /**
     * Builds a merged {@link Properties} applying the full hierarchy:
     * <ol>
     *   <li>defaults</li>
     *   <li>Global system properties (per scope, in order)</li>
     *   <li>Topic-specific system properties (per scope, in order) — if topic is non-null</li>
     *   <li>Global customizers (per scope, in order)</li>
     *   <li>Topic-specific customizers (per scope, in order) — if topic is non-null</li>
     * </ol>
     *
     * @param defaults  baseline properties (serializers, bootstrap servers, etc.)
     * @param topic     the topic name for topic-specific overrides, or null for global only
     * @param scopes    the scopes to apply, in order of increasing priority
     * @return merged properties ready for Kafka client creation
     */
    public static Properties buildProperties(Properties defaults, String topic, KafkaClientType... scopes) {
        Properties result = new Properties();
        result.putAll(defaults);

        // 1. Global system properties
        for (KafkaClientType scope : scopes) {
            applySystemProperties(result, scope.prefix());
        }

        // 2. Topic-specific system properties
        if (topic != null) {
            for (KafkaClientType scope : scopes) {
                applySystemProperties(result, TOPIC_PREFIX + topic + "." + scope.scopeName() + ".");
            }
        }

        // 3. Global customizers
        for (KafkaClientType scope : scopes) {
            applyCustomizers(result, globalCustomizers.get(scope));
        }

        // 4. Topic-specific customizers
        if (topic != null) {
            Map<KafkaClientType, List<Consumer<Properties>>> topicMap = topicCustomizers.get(topic);
            if (topicMap != null) {
                for (KafkaClientType scope : scopes) {
                    applyCustomizers(result, topicMap.get(scope));
                }
            }
        }

        return result;
    }

    // ===== Internal helpers =====

    private static void applySystemProperties(Properties target, String prefix) {
        System.getProperties().stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix))
                .forEach(key -> {
                    String kafkaProperty = key.substring(prefix.length());
                    if (!kafkaProperty.isEmpty()) {
                        target.put(kafkaProperty, System.getProperty(key));
                    }
                });
    }

    private static void applyCustomizers(Properties target, List<Consumer<Properties>> customizerList) {
        if (customizerList != null) {
            for (Consumer<Properties> customizer : customizerList) {
                customizer.accept(target);
            }
        }
    }
}
