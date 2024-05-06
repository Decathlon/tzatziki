package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.front.integration.Browser;
import com.decathlon.tzatziki.front.playwright.integration.PlaywrightBrowser;
import io.cucumber.java.BeforeAll;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlaywrightBrowserFactory implements BrowserFactory {

    @BeforeAll
    public static void init() {
    }

    @Override
    public Browser createBrowser() {
        return new PlaywrightBrowser();
    }
}
