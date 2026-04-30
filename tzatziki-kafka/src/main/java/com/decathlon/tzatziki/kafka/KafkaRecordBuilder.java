package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.utils.Mapper;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utility class for building Kafka ProducerRecords from maps and Avro schemas.
 * Shared between tzatziki-kafka and tzatziki-spring-kafka.
 */
public class KafkaRecordBuilder {

    private static final String VALUE_KEY = "value";
    private static final String HEADERS_KEY = "headers";

    private KafkaRecordBuilder() {
    }

    @SuppressWarnings("unchecked")
    public static @NotNull GenericRecord buildGenericRecordMessage(Schema schemaMessage, Map<?, Object> avroRecord) {
        GenericRecordBuilder genericRecordBuilderMessage = new GenericRecordBuilder(schemaMessage);
        if (avroRecord.get(VALUE_KEY) != null) {
            ((Map<String, Object>) avroRecord.get(VALUE_KEY))
                    .forEach((fieldName, value) -> genericRecordBuilderMessage.set(fieldName, wrapIn(value, schemaMessage.getField(fieldName).schema())));
        }
        return genericRecordBuilderMessage.build();
    }

    @SuppressWarnings("unchecked")
    public static Object wrapIn(Object value, Schema schema) {
        if (schema.getType().equals(Schema.Type.RECORD)) {
            GenericRecordBuilder genericRecordBuilder = new GenericRecordBuilder(schema);
            ((Map<String, Object>) value).forEach((f, v) -> genericRecordBuilder.set(f, wrapIn(v, schema.getField(f).schema())));
            return genericRecordBuilder.build();
        } else if (schema.getType().equals(Schema.Type.ARRAY)) {
            Schema elementType = schema.getElementType();
            return ((List<?>) value).stream().map(element -> wrapIn(element, elementType)).toList();
        } else if (schema.getType().equals(Schema.Type.ENUM)) {
            return new GenericData.EnumSymbol(schema, value);
        } else if (schema.getType().equals(Schema.Type.UNION) && value != null) {
            Schema elementType = schema.getTypes().stream()
                    .filter(type -> type.getType() != Schema.Type.NULL)
                    .findFirst()
                    .orElseThrow();
            return wrapIn(value, elementType);
        }
        if (value instanceof String string && !schema.getType().equals(Schema.Type.STRING)) {
            value = parseAvro(string, schema);
        }
        return value;
    }

    @Nullable
    public static Object parseAvro(String value, Schema valueSchema) {
        return switch (valueSchema.getType()) {
            case INT -> Integer.parseInt(value);
            case LONG -> Long.parseLong(value);
            case FLOAT -> Float.parseFloat(value);
            case DOUBLE -> Double.parseDouble(value);
            case BOOLEAN -> Boolean.parseBoolean(value);
            default -> value;
        };
    }

    @SuppressWarnings("unchecked")
    public static ProducerRecord<GenericRecord, GenericRecord> mapToAvroKeyMessageRecord(
            Schema schemaMessage, Schema schemaKey, String topic, Map<?, Object> avroRecord) {
        GenericRecord genericRecordMessage = buildGenericRecordMessage(schemaMessage, avroRecord);

        GenericRecordBuilder genericRecordBuilderKey = new GenericRecordBuilder(schemaKey);
        Map<String, Object> keyValue = (Map<String, Object>) avroRecord.get("key");
        keyValue.forEach((fieldName, value) -> genericRecordBuilderKey.set(fieldName, wrapIn(value, schemaKey.getField(fieldName).schema())));
        GenericData.Record recordKey = genericRecordBuilderKey.build();

        ProducerRecord<GenericRecord, GenericRecord> producerRecord = new ProducerRecord<>(topic, recordKey, genericRecordMessage);
        Map<String, String> headers = (Map<String, String>) avroRecord.get(HEADERS_KEY);
        if (headers != null) {
            headers.forEach((k, value) -> producerRecord.headers().add(k, value.getBytes(UTF_8)));
        }

        return producerRecord;
    }

    @SuppressWarnings("unchecked")
    public static ProducerRecord<String, GenericRecord> mapToAvroRecord(
            Schema schema, String topic, Map<String, Object> avroRecord) {
        GenericRecord genericRecordMessage = buildGenericRecordMessage(schema, avroRecord);

        String messageKey = (String) avroRecord.get("key");

        ProducerRecord<String, GenericRecord> producerRecord = new ProducerRecord<>(topic, messageKey, genericRecordMessage);
        Map<String, String> headers = (Map<String, String>) avroRecord.get(HEADERS_KEY);
        if (headers != null) {
            headers.forEach((key, value) -> producerRecord.headers().add(key, value != null ? value.getBytes(UTF_8) : null));
        }

        return producerRecord;
    }

    @SuppressWarnings("unchecked")
    public static ProducerRecord<String, String> mapToJsonRecord(String topic, Map<?, Object> jsonRecord) {
        String messageKey = (String) jsonRecord.get("key");
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, messageKey, Mapper.toJson(jsonRecord.get(VALUE_KEY)));
        Map<String, String> headers = (Map<String, String>) jsonRecord.get(HEADERS_KEY);
        if (headers != null) {
            headers.forEach((key, value) -> producerRecord.headers().add(key, value != null ? value.getBytes(UTF_8) : null));
        }

        return producerRecord;
    }
}
