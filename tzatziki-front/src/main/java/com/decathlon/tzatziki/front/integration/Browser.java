package com.decathlon.tzatziki.front.integration;

import com.decathlon.tzatziki.front.interactions.Action;
import com.decathlon.tzatziki.front.interactions.models.HTMLElement;

import java.util.List;

/**
 * selector input parameter can be of type CSS or XPath
 */
public interface Browser {
    void get(String page);

    List<HTMLElement> find(String selector);

    default List<HTMLElement> actionOn(String selector, Action action, String... params) {
        return this.find(selector).stream().map(htmlElement -> htmlElement.performAction(action, params)).toList();
    }

    boolean waitForElement(String selector, Boolean isVisible, Integer timeoutMs);

    boolean waitForPage(String page, Integer timeoutMs);

    void reload();
}
