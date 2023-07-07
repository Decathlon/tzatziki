package com.decathlon.tzatziki.front.integration;

import java.util.Optional;

public enum BrowserType {
    CHROME, FIREFOX, EDGE, IE, SAFARI;

    public static final String BROWSER_ENV_VAR = "TZATZIKI_BROWSER";

    public static BrowserType get() {
        return Optional.ofNullable(System.getenv(BROWSER_ENV_VAR))
                .map(String::toUpperCase)
                .map(BrowserType::valueOf)
                .orElse(CHROME);
    }
}
