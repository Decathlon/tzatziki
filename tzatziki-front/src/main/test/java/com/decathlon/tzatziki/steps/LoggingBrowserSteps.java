package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.LoggingBrowser;
import com.decathlon.tzatziki.front.integration.Browser;
import io.cucumber.java.BeforeAll;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingBrowserSteps implements BrowserSteps {

    @BeforeAll
    public static void init() {
    }

    @Override
    public Browser createBrowser() {
        return new LoggingBrowser();
    }
}
