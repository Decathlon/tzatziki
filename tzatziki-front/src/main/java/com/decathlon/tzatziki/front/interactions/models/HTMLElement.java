package com.decathlon.tzatziki.front.interactions.models;

import com.decathlon.tzatziki.front.interactions.Action;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public interface HTMLElement {
    String getId();
    List<String> getClasses();
    Map<String, Object> getAttributes();
    default <T> T getAttribute(String attribute) {
        return (T) getAttributes().get(attribute);
    };
    String getStyleValue(String styleProperty);
    HTMLElement performAction(Action action, String... params);
}
