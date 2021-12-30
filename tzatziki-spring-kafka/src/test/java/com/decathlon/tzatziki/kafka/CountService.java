package com.decathlon.tzatziki.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Optional.ofNullable;

@Slf4j
@Service
public class CountService {
    private final Map<String, Integer> messageCountsPerTopic = new LinkedHashMap<>();

    public Map<String, Integer> messageCountsPerTopic() {
        return messageCountsPerTopic;
    }

    public void countMessage(String t) {
        messageCountsPerTopic.compute(t, (topic, count) -> ofNullable(count).orElse(0) + 1);
    }
}
