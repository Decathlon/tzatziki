package com.decathlon.tzatziki.kafka;

/**
 * Defines the scopes for Kafka client configuration properties.
 * <p>
 * Properties are applied in order of increasing specificity:
 * {@link #COMMON} → {@link #PRODUCER}/{@link #CONSUMER}/{@link #ADMIN} → format-specific ({@link #AVRO_PRODUCER}, etc.)
 * <p>
 * Each scope maps to a system property prefix: {@code tzatziki.kafka.<scope-prefix>.<kafka-property>}.
 */
public enum KafkaClientType {

    /** Applied to all Kafka clients (producers, consumers, admin). */
    COMMON("tzatziki.kafka.common.", "common"),

    /** Applied to all producers (avro + json). */
    PRODUCER("tzatziki.kafka.producer.", "producer"),

    /** Applied to all consumers (avro + json). */
    CONSUMER("tzatziki.kafka.consumer.", "consumer"),

    /** Applied to the admin client only. */
    ADMIN("tzatziki.kafka.admin.", "admin"),

    /** Applied to avro producers only. */
    AVRO_PRODUCER("tzatziki.kafka.avro-producer.", "avro-producer"),

    /** Applied to json producers only. */
    JSON_PRODUCER("tzatziki.kafka.json-producer.", "json-producer"),

    /** Applied to avro consumers only. */
    AVRO_CONSUMER("tzatziki.kafka.avro-consumer.", "avro-consumer"),

    /** Applied to json consumers only. */
    JSON_CONSUMER("tzatziki.kafka.json-consumer.", "json-consumer");

    private final String prefix;
    private final String scopeName;

    KafkaClientType(String prefix, String scopeName) {
        this.prefix = prefix;
        this.scopeName = scopeName;
    }

    /**
     * Returns the global system property prefix, e.g. {@code "tzatziki.kafka.common."}.
     */
    public String prefix() {
        return prefix;
    }

    /**
     * Returns the scope name used in topic-specific prefixes, e.g. {@code "common"}.
     * Topic-specific prefix is: {@code tzatziki.kafka.topic.<topic-name>.<scopeName>.}
     */
    public String scopeName() {
        return scopeName;
    }
}
