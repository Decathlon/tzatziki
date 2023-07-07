package com.decathlon.tzatziki.front.playwright.integration;

import com.decathlon.tzatziki.front.interactions.Action;
import com.decathlon.tzatziki.front.interactions.models.HTMLElement;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;


public class PlaywrightBrowserTest {
    @Test
    public void find() {
        PlaywrightBrowser browser = new PlaywrightBrowser();

        browser.get("https://www.google.com");

        List<HTMLElement> htmlElements = browser.actionOn("form[action=\"/search\"] textarea", Action.FILL, "Hello World!");

        assertThat(htmlElements, Matchers.hasSize(1));
        assertThat(htmlElements.get(0).getAttribute("name"), Matchers.equalTo("q"));
        assertThat(htmlElements.get(0).getAttribute("inputValue"), Matchers.equalTo("Hello World!"));
        assertThat(htmlElements.get(0).getStyleValue("display"), Matchers.equalTo("flex"));
    }
}
