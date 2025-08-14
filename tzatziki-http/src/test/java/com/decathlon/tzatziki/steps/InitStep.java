package com.decathlon.tzatziki.steps;

import io.cucumber.java.BeforeAll;

public class InitStep {
    @BeforeAll
    public static void beforeAll() {
        System.setProperty("tzatziki.http.max-concurrent-requests", "1");
    }
}
