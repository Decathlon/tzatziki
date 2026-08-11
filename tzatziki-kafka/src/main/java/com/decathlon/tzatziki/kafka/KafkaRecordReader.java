package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.utils.Mapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utility class for reading/converting Kafka ConsumerRecords to Maps.
 * Shared between tzatziki-kafka and tzatziki-spring-kafka.
 */
public class KafkaRecordReader {

    private static final String VALUE_KEY = "value";
    private static final String HEADERS_KEY = "headers";

    private KafkaRecordReader() {
    }

    public static List<Map<String, Object>> consumerRecordsToMaps(Iterable<?> records) {
        return StreamSupport.stream(records.spliterator(), false)
                .map(r -> {
                    ConsumerRecord<?, ?> consumerRecord = (ConsumerRecord<?, ?>) r;
                    Map<String, String> headers = Stream.of(consumerRecord.headers().toArray())
                            .collect(HashMap::new,
                                    (map, header) -> map.put(header.key(), header.value() != null ? new String(header.value(), UTF_8) : null),
                                    HashMap::putAll);
                    Map<String, Object> value = consumerRecord.value() != null
                            ? Mapper.read(consumerRecord.value().toString())
                            : Collections.emptyMap();
                    String messageKey = consumerRecord.key() != null ? String.valueOf(consumerRecord.key()) : "";
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put(VALUE_KEY, value);
                    result.put(HEADERS_KEY, headers);
                    result.put("key", messageKey);
                    return result;
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<?, Object>> asListOfRecordsWithHeaders(Object content) {
        List<Map<Object, Object>> records = content instanceof Map
                ? List.of((Map<Object, Object>) content)
                : (List<Map<Object, Object>>) content;
        return records.stream()
                .map(entry -> {
                    final int entrySize = entry.size();
                    if (2 <= entrySize && entrySize <= 3 && entry.containsKey(VALUE_KEY) && entry.containsKey(HEADERS_KEY)) {
                        return entry;
                    }
                    return Map.<String, Object>of(VALUE_KEY, entry, HEADERS_KEY, new LinkedHashMap<>());
                }).toList();
    }
}
