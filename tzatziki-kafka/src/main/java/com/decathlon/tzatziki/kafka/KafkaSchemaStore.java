package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.steps.ObjectSteps;
import org.apache.avro.Schema;

import static java.util.Locale.ROOT;

/**
 * Utility class for storing and retrieving Avro schemas from the ObjectSteps context.
 * Shared between tzatziki-kafka and tzatziki-spring-kafka.
 */
public class KafkaSchemaStore {

    private static final String SCHEMA_PREFIX = "_kafka.schemas.";

    private KafkaSchemaStore() {
    }

    public static void storeSchema(ObjectSteps objects, String name, Schema schema) {
        objects.add(SCHEMA_PREFIX + name.toLowerCase(ROOT), schema);
    }

    public static Schema getSchema(ObjectSteps objects, String name) {
        Object schema = objects.getOrSelf(SCHEMA_PREFIX + name);
        if (schema instanceof Schema avroSchema) {
            return avroSchema;
        }
        schema = objects.getOrSelf(SCHEMA_PREFIX + name.substring(0, name.length() - 1));
        if (!(schema instanceof Schema)) {
            throw new IllegalArgumentException(
                    "The Avro schema for '" + name + "' was not found. You can follow the steps below to solve the issue:\n" +
                            "- ensure that the schema .avsc file has been correctly added using the 'avro schema' step. Doc: https://github.com/Decathlon/tzatziki/tree/main/tzatziki-kafka#defining-an-avro-schema\n" +
                            "- confirm that the object '" + name + "' in your step matches the value of the 'name' property defined in the Avro schema.\n");
        }
        return (Schema) schema;
    }
}
