package com.decathlon.tzatziki.front.integration;

import com.decathlon.tzatziki.front.interactions.Action;
import com.decathlon.tzatziki.front.interactions.models.HTMLElement;

import java.util.List;

public interface Browser {
    void get(String page);

    List<HTMLElement> find(String cssSelector);

    default List<HTMLElement> actionOn(String cssSelector, Action action, String... params) {
        return this.find(cssSelector).stream().map(htmlElement -> htmlElement.performAction(action, params)).toList();
    }
}
