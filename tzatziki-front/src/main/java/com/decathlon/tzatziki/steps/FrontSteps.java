package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.front.integration.Browser;
import com.decathlon.tzatziki.utils.Guard;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;

import static com.decathlon.tzatziki.utils.Patterns.QUOTED_CONTENT;
import static com.decathlon.tzatziki.utils.Patterns.THAT;

@Slf4j
public class FrontSteps {

    private final ObjectSteps objects;
    private final BrowserFactory browserFactory;
    private Browser browser;

    public FrontSteps(ObjectSteps objects, BrowserFactory browserFactory) {
        this.objects = objects;
        this.browserFactory = browserFactory;
    }

    @Before(order = -1) // just for this instance to be created
    public void before() {
        browser = browserFactory.createBrowser();
    }


    @Given(THAT + Guard.GUARD + "browser navigate to " + QUOTED_CONTENT + "$")
    public void we_navigate_to_url(Guard guard, String url) {
        guard.in(objects, () -> browser.get(url));
    }

    @Then(Guard.GUARD + "browser url is " + QUOTED_CONTENT + "$")
    public void we_resume_replaying_a_topic(Guard guard, String url) {
        guard.in(objects, () -> browser.waitForPage(url, 1000));
    }

    @After
    public void after() {
        browser.close();
    }
}
