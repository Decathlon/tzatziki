package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.utils.Mapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Utility class for reading/converting Kafka ConsumerRecords to Maps.
 * Shared between tzatziki-kafka and tzatziki-spring-kafka.
 */
public class KafkaRecordReader {

    private KafkaRecordReader() {
    }

    public static List<Map<String, Object>> consumerRecordsToMaps(Iterable<?> records) {
        return StreamSupport.stream(records.spliterator(), false)
                .map(r -> {
                    ConsumerRecord<?, ?> record = (ConsumerRecord<?, ?>) r;
                    Map<String, String> headers = Stream.of(record.headers().toArray())
                            .collect(HashMap::new,
                                    (map, header) -> map.put(header.key(), header.value() != null ? new String(header.value()) : null),
                                    HashMap::putAll);
                    Map<String, Object> value = Mapper.read(record.value().toString());
                    String messageKey = record.key() != null ? String.valueOf(record.key()) : "";
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("value", value);
                    result.put("headers", headers);
                    result.put("key", messageKey);
                    return result;
                })
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public static List<Map<?, Object>> asListOfRecordsWithHeaders(Object content) {
        List<Map<Object, Object>> records = content instanceof Map
                ? List.of((Map<Object, Object>) content)
                : (List<Map<Object, Object>>) content;
        return records.stream()
                .map(record -> {
                    final int recordSize = record.size();
                    if (2 <= recordSize && recordSize <= 3 && record.containsKey("value") && record.containsKey("headers")) {
                        return record;
                    }
                    return Map.<String, Object>of("value", record, "headers", new LinkedHashMap<>());
                }).collect(Collectors.toList());
    }
}
