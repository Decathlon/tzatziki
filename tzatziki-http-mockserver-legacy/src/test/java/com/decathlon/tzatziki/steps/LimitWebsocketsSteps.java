package com.decathlon.tzatziki.steps;

import io.cucumber.java.Before;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LimitWebsocketsSteps {
    @Before(order = -2)
    public void before() {
        System.setProperty("mockserver.maxWebSocketExpectations", "200");
    }
}
