package com.decathlon.tzatziki;

import com.decathlon.tzatziki.front.integration.Browser;
import com.decathlon.tzatziki.front.interactions.models.HTMLElement;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class LoggingBrowser implements Browser {
    private String currentPage;

    @Override
    public void get(String page) {
        currentPage = page;
        log.info("Getting page {}", page);
    }

    @Override
    public List<HTMLElement> find(String selector) {
        log.info("Finding element {}", selector);
        return List.of(new LoggingHTMLElement());
    }

    @Override
    public boolean waitForElement(String selector, boolean isVisible, Integer timeoutMs) {
        log.info("Waiting element with selector {}, visible {} and timeout {}ms", selector, isVisible, timeoutMs);
        return true;
    }

    @Override
    public boolean waitForPage(String page, Integer timeoutMs) {
        log.info("Waiting page {} with timeout {}ms", page, timeoutMs);
        return currentPage.equals(page);
    }

    @Override
    public void reload() {

    }

    @Override
    public void close() {

    }
}
