package com.decathlon.tzatziki.kafka;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Configuration properties for tzatziki-kafka, read from system properties and/or programmatic customizers.
 * <p>
 * <b>Property resolution hierarchy</b> (later overrides earlier):
 * <ol>
 *   <li>Defaults (serializers, bootstrap servers, etc. — set by the backend)</li>
 *   <li>Global system properties: {@code tzatziki.kafka.<kafka-property>}</li>
 *   <li>Global customizers (registered via {@link #customize(Consumer)})</li>
 *   <li>Cluster customizer (if topic maps to a cluster via {@link #mapTopicToCluster})</li>
 *   <li>Topic-specific system properties: {@code tzatziki.kafka.topic.<topic-name>.<kafka-property>}</li>
 *   <li>Topic-specific customizers (registered via {@link #customize(String, Consumer)})</li>
 * </ol>
 * <p>
 * <b>Cluster abstraction:</b> Use {@link #defineCluster(String, Consumer)} to declare reusable configuration
 * (bootstrap servers, SSL, schema registry, etc.), then {@link #mapTopicToCluster(String, String)} to assign
 * topics. This avoids duplicating configuration when multiple topics share the same Kafka cluster.
 * <p>
 * Set via {@code -D} flags, e.g.: {@code -Dtzatziki.kafka.security.protocol=SSL}
 */
public class KafkaConfigurationProperties {

    private KafkaConfigurationProperties() {
    }

    private static final String GLOBAL_PREFIX = "tzatziki.kafka.";
    private static final String TOPIC_PREFIX = "tzatziki.kafka.topic.";

    // ===== Well-known property keys =====

    public static final String BOOTSTRAP_SERVERS = "tzatziki.kafka.bootstrap-servers";
    public static final String SCHEMA_REGISTRY_URL = "tzatziki.kafka.schema-registry-url";
    public static final String CONSUMER_GROUP_ID = "tzatziki.kafka.consumer-group-id";
    public static final String CONSUMER_AUTO_OFFSET_RESET = "tzatziki.kafka.consumer-auto-offset-reset";

    // ===== Customizer registry =====

    private static final List<Consumer<Properties>> globalCustomizers = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, List<Consumer<Properties>>> topicCustomizers = new ConcurrentHashMap<>();

    // ===== Cluster registry =====

    private static final Map<String, Consumer<Properties>> clusters = new ConcurrentHashMap<>();
    private static final Map<String, String> topicToCluster = new ConcurrentHashMap<>();

    /**
     * Register a global programmatic customizer applied to all Kafka clients.
     * Customizers are applied after global system properties.
     */
    public static void customize(Consumer<Properties> customizer) {
        globalCustomizers.add(customizer);
    }

    /**
     * Register a topic-specific programmatic customizer.
     * Topic customizers have the highest priority in the resolution hierarchy.
     * <p>
     * Example:
     * <pre>{@code
     * KafkaConfigurationProperties.customize("orders", props -> {
     *     props.put("max.poll.records", "100");
     * });
     * }</pre>
     */
    public static void customize(String topic, Consumer<Properties> customizer) {
        topicCustomizers.computeIfAbsent(topic, k -> Collections.synchronizedList(new ArrayList<>())).add(customizer);
    }

    /**
     * Define a named cluster with shared configuration.
     * Cluster customizers are applied after global customizers but before topic-specific customizers,
     * making them ideal for shared infrastructure config (bootstrap servers, SSL, schema registry).
     * <p>
     * Example:
     * <pre>{@code
     * KafkaConfigurationProperties.defineCluster("production", props -> {
     *     props.put("bootstrap.servers", "kafka-prod:9093");
     *     props.put("security.protocol", "SSL");
     * });
     * }</pre>
     */
    public static void defineCluster(String name, Consumer<Properties> customizer) {
        clusters.put(name, customizer);
    }

    /**
     * Map a topic to a named cluster. When building properties for this topic,
     * the cluster's customizer will be applied after global customizers.
     * <p>
     * Example:
     * <pre>{@code
     * KafkaConfigurationProperties.mapTopicToCluster("orders", "production");
     * KafkaConfigurationProperties.mapTopicToCluster("payments", "production");
     * }</pre>
     *
     * @throws IllegalArgumentException if the cluster has not been defined
     */
    public static void mapTopicToCluster(String topic, String clusterName) {
        if (!clusters.containsKey(clusterName)) {
            throw new IllegalArgumentException("Unknown cluster: " + clusterName
                    + ". Define it first with defineCluster().");
        }
        topicToCluster.put(topic, clusterName);
    }

    /**
     * Reset all registered customizers, clusters, and topic mappings.
     */
    public static void resetCustomizers() {
        globalCustomizers.clear();
        topicCustomizers.clear();
        clusters.clear();
        topicToCluster.clear();
    }

    // ===== Well-known accessors =====

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
     * Builds properties without topic-specific overrides (global only).
     *
     * @param defaults baseline properties (serializers, bootstrap servers, etc.)
     * @return merged properties ready for Kafka client creation
     */
    public static Properties buildProperties(Properties defaults) {
        return buildProperties(defaults, null);
    }

    /**
     * Builds a merged {@link Properties} applying the full hierarchy:
     * <ol>
     *   <li>defaults</li>
     *   <li>Global system properties ({@code tzatziki.kafka.*})</li>
     *   <li>Global customizers</li>
     *   <li>Cluster customizer — if topic maps to a cluster</li>
     *   <li>Topic-specific system properties ({@code tzatziki.kafka.topic.<name>.*})</li>
     *   <li>Topic-specific customizers</li>
     * </ol>
     *
     * @param defaults baseline properties (serializers, bootstrap servers, etc.)
     * @param topic    the topic name for topic/cluster overrides, or null for global only
     * @return merged properties ready for Kafka client creation
     */
    public static Properties buildProperties(Properties defaults, String topic) {
        Properties result = new Properties();
        result.putAll(defaults);

        // 1. Global system properties
        applySystemProperties(result, GLOBAL_PREFIX, TOPIC_PREFIX);

        // 2. Global customizers
        applyCustomizers(result, globalCustomizers);

        // 3. Cluster customizer
        if (topic != null) {
            String clusterName = topicToCluster.get(topic);
            if (clusterName != null) {
                Consumer<Properties> clusterCustomizer = clusters.get(clusterName);
                if (clusterCustomizer != null) {
                    clusterCustomizer.accept(result);
                }
            }
        }

        // 4. Topic-specific system properties
        if (topic != null) {
            applySystemProperties(result, TOPIC_PREFIX + topic + ".");
        }

        // 5. Topic-specific customizers
        if (topic != null) {
            applyCustomizers(result, topicCustomizers.get(topic));
        }

        return result;
    }

    // ===== Internal helpers =====

    private static void applySystemProperties(Properties target, String prefix) {
        applySystemProperties(target, prefix, null);
    }

    private static void applySystemProperties(Properties target, String prefix, String excludePrefix) {
        System.getProperties().stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix))
                .filter(key -> excludePrefix == null || !key.startsWith(excludePrefix))
                .forEach(key -> {
                    String kafkaProperty = key.substring(prefix.length());
                    if (!kafkaProperty.isEmpty()) {
                        target.put(kafkaProperty, System.getProperty(key));
                    }
                });
    }

    private static void applyCustomizers(Properties target, List<Consumer<Properties>> customizerList) {
        if (customizerList != null) {
            List<Consumer<Properties>> snapshot;
            synchronized (KafkaConfigurationProperties.class) {
                snapshot = new ArrayList<>(customizerList);
            }
            for (Consumer<Properties> customizer : snapshot) {
                customizer.accept(target);
            }
        }
    }
}
