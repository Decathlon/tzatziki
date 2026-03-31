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
 * </ol>
 * <p>
 * <b>Programmatic customizers:</b> Register a {@link Consumer Consumer&lt;Properties&gt;} per {@link KafkaClientType}
 * via {@link #customize(KafkaClientType, Consumer)}. Customizers are applied after system properties.
 * <p>
 * Set via {@code -D} flags, e.g.: {@code -Dtzatziki.kafka.common.security.protocol=SSL}
 */
public class KafkaConfigurationProperties {

    private KafkaConfigurationProperties() {
    }

    // ===== Legacy property keys (backward compatible) =====

    public static final String BOOTSTRAP_SERVERS = "tzatziki.kafka.bootstrap-servers";
    public static final String SCHEMA_REGISTRY_URL = "tzatziki.kafka.schema-registry-url";
    public static final String CONSUMER_GROUP_ID = "tzatziki.kafka.consumer.group-id";
    public static final String CONSUMER_AUTO_OFFSET_RESET = "tzatziki.kafka.consumer.auto-offset-reset";

    // ===== Customizer registry =====

    private static final Map<KafkaClientType, List<Consumer<Properties>>> customizers = new ConcurrentHashMap<>();

    /**
     * Register a programmatic customizer for a specific client type.
     * Customizers are applied after system property prefix forwarding, allowing final overrides.
     * <p>
     * Example:
     * <pre>{@code
     * KafkaConfigurationProperties.customize(KafkaClientType.COMMON, props -> {
     *     props.put("security.protocol", "SSL");
     *     props.put("ssl.keystore.location", "/path/to/keystore.p12");
     * });
     * }</pre>
     */
    public static void customize(KafkaClientType scope, Consumer<Properties> customizer) {
        customizers.computeIfAbsent(scope, k -> new ArrayList<>()).add(customizer);
    }

    /**
     * Reset all registered customizers. Call in test teardown if needed.
     */
    public static void resetCustomizers() {
        customizers.clear();
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
     * Builds a merged {@link Properties} from the given defaults, then overlays system properties
     * matching the given scopes (in order), then applies any registered customizers.
     *
     * @param defaults  baseline properties (serializers, bootstrap servers, etc.)
     * @param scopes    the scopes to apply, in order of increasing priority
     * @return merged properties ready for Kafka client creation
     */
    public static Properties buildProperties(Properties defaults, KafkaClientType... scopes) {
        Properties result = new Properties();
        result.putAll(defaults);

        for (KafkaClientType scope : scopes) {
            applySystemProperties(result, scope);
        }

        for (KafkaClientType scope : scopes) {
            applyCustomizers(result, scope);
        }

        return result;
    }

    // ===== Internal helpers =====

    private static void applySystemProperties(Properties target, KafkaClientType scope) {
        String prefix = scope.prefix();
        System.getProperties().stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix))
                .forEach(key -> {
                    String kafkaProperty = key.substring(prefix.length());
                    target.put(kafkaProperty, System.getProperty(key));
                });
    }

    private static void applyCustomizers(Properties target, KafkaClientType scope) {
        List<Consumer<Properties>> scopeCustomizers = customizers.get(scope);
        if (scopeCustomizers != null) {
            for (Consumer<Properties> customizer : scopeCustomizers) {
                customizer.accept(target);
            }
        }
    }
}
