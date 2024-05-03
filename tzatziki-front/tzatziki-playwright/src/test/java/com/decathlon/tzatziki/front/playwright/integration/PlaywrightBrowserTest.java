package com.decathlon.tzatziki.front.playwright.integration;

import com.decathlon.tzatziki.front.interactions.Action;
import com.decathlon.tzatziki.front.interactions.models.HTMLElement;
import com.google.common.io.Resources;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;


public class PlaywrightBrowserTest {
    private static ClientAndServer mockServer;
    private static String url;

    @BeforeAll
    public static void startMockServer() throws IOException {
        mockServer = new ClientAndServer();
        url = "http://localhost:" + mockServer.getLocalPort();
        mockServer.when(
                        request()
                                .withMethod("GET")
                                .withPath("/form.html")
                )
                .respond(
                        response()
                                .withBody(Resources.toString(Resources.getResource("form.html"), StandardCharsets.UTF_8))
                                .withContentType(MediaType.TEXT_HTML)
                );
        mockServer.when(
                        request()
                                .withMethod("GET")
                                .withPath("/form_with_delay")
                )
                .respond(
                        response()
                                .withBody(Resources.toString(Resources.getResource("form.html"), StandardCharsets.UTF_8))
                                .withContentType(MediaType.TEXT_HTML)
                                .withDelay(TimeUnit.SECONDS, 3)
                );
    }

    @Test
    void find() {
        PlaywrightBrowser browser = new PlaywrightBrowser();

        browser.get(url + "/form.html");

        List<HTMLElement> htmlElementsCss = browser.find("form[action=\"/search\"] textarea");

        assertThat(htmlElementsCss, Matchers.hasSize(1));
        assertThat(htmlElementsCss.get(0).getAttribute("name"), Matchers.equalTo("q"));
        assertThat(htmlElementsCss.get(0).getStyleValue("display"), Matchers.equalTo("flex"));

        List<HTMLElement> htmlElementsXPath = browser.find("//form[@action=\"/search\"]//textarea");

        assertThat(htmlElementsXPath, Matchers.hasSize(1));
        assertThat(htmlElementsXPath.get(0).getAttribute("name"), Matchers.equalTo("q"));
        assertThat(htmlElementsXPath.get(0).getStyleValue("display"), Matchers.equalTo("flex"));
    }

    @Test
    void actionOn() {
        PlaywrightBrowser browser = new PlaywrightBrowser();

        browser.get(url + "/form.html");

        List<HTMLElement> htmlElements = browser.actionOn("form[action=\"/search\"] textarea", Action.FILL, "Hello World!");

        assertThat(htmlElements, Matchers.hasSize(1));
        assertThat(htmlElements.get(0).getAttribute("name"), Matchers.equalTo("q"));
        assertThat(htmlElements.get(0).getAttribute("inputValue"), Matchers.equalTo("Hello World!"));
        assertThat(htmlElements.get(0).getStyleValue("display"), Matchers.equalTo("flex"));
    }

    @Test
    void waitForElement() {
        PlaywrightBrowser browser = new PlaywrightBrowser();
        browser.get(url + "/form.html");

        //at first visible_button is display none
        Assertions.assertThat(browser.waitForElement("#visible_button", false, 1000)).isTrue();
        Assertions.assertThat(browser.waitForElement("#visible_button", true, 1000)).isFalse();

        // after 2 seconds visible_button is display block
        Assertions.assertThat(browser.waitForElement("#visible_button", true, 5000)).isTrue();
    }

    @Test
    void waitForPage() {
        PlaywrightBrowser browser = new PlaywrightBrowser();
        browser.get(url + "/form.html");
        browser.actionOn("#form_with_delay", Action.CLICK);

        Assertions.assertThat(browser.waitForPage(url + "/form_with_delay", 100)).isFalse();
        Assertions.assertThat(browser.waitForPage(url + "/form_with_delay", 5000)).isTrue();
    }

    @Test
    void reloadPage() {
        PlaywrightBrowser browser = new PlaywrightBrowser();
        browser.get(url + "/form.html");
        assertThat(browser.find("#is_reload").get(0).getAttribute("inputValue"), Matchers.equalTo("no"));

        browser.reload();

        assertThat(browser.find("#is_reload").get(0).getAttribute("inputValue"), Matchers.equalTo("true"));
    }

    @AfterAll
    public static void stopMockServer() {
        mockServer.stop();
    }
}
