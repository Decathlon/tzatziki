package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.steps.ObjectSteps;
import com.decathlon.tzatziki.utils.Mapper;
import org.apache.avro.Schema;

import static java.util.Locale.ROOT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Utility class for storing and retrieving Avro schemas from the ObjectSteps context.
 * Shared between tzatziki-kafka and tzatziki-spring-kafka.
 */
public class KafkaSchemaStore {

    private KafkaSchemaStore() {
    }

    public static void storeSchema(ObjectSteps objects, String name, Schema schema) {
        objects.add("_kafka.schemas." + name.toLowerCase(ROOT), schema);
    }

    public static Schema getSchema(ObjectSteps objects, String name) {
        Object schema = objects.getOrSelf("_kafka.schemas." + name);
        if (schema instanceof Schema avroSchema) {
            return avroSchema;
        }
        schema = objects.getOrSelf("_kafka.schemas." + name.substring(0, name.length() - 1));
        assertThat(schema)
                .overridingErrorMessage(
                        "The Avro schema for '" + name + "' was not found. You can follow the steps below to solve the issue:\n" +
                                "- ensure that the schema .avsc file has been correctly added using the 'avro schema' step. Doc: https://github.com/Decathlon/tzatziki/tree/main/tzatziki-kafka#defining-an-avro-schema\n" +
                                "- confirm that the object '" + name + "' in your step matches the value of the 'name' property defined in the Avro schema.\n")
                .isInstanceOf(Schema.class);
        return (Schema) schema;
    }
}
