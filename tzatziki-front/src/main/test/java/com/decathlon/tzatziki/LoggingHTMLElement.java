package com.decathlon.tzatziki;

import com.decathlon.tzatziki.front.interactions.Action;
import com.decathlon.tzatziki.front.interactions.models.HTMLElement;
import com.google.common.base.Joiner;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
public class LoggingHTMLElement implements HTMLElement {

    @Override
    public String getId() {
        return null;
    }

    @Override
    public List<String> getClasses() {
        return null;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("key1", "value1",
                "key2", "value2",
                "key3", "value3",
                "key4", "value4");
    }

    @Override
    public String getStyleValue(String styleProperty) {
        return null;
    }

    @Override
    public HTMLElement performAction(Action action, String... params) {
        log.info("Action performed {} with params {}", action, Joiner.on(", ").join(params));
        return this;
    }
}
