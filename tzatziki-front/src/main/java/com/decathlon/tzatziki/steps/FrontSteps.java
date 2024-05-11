package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.front.integration.Browser;
import com.decathlon.tzatziki.front.interactions.Action;
import com.decathlon.tzatziki.front.interactions.BrowserAssertion;
import com.decathlon.tzatziki.front.interactions.HTMLElementAssertion;
import com.decathlon.tzatziki.front.interactions.models.HTMLElement;
import com.decathlon.tzatziki.utils.Guard;
import io.cucumber.java.After;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.List;

import static com.decathlon.tzatziki.front.interactions.BrowserAssertion.STEPS_PATTERN;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.A_DURATION;
import static com.decathlon.tzatziki.utils.Patterns.A_USER;
import static com.decathlon.tzatziki.utils.Patterns.QUOTED_CONTENT;
import static com.decathlon.tzatziki.utils.Patterns.THAT;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class FrontSteps {

    static {
        DynamicTransformers.register(BrowserAssertion.class, BrowserAssertion::parse);
        DynamicTransformers.register(HTMLElementAssertion.class, HTMLElementAssertion::parse);
    }

    private final ObjectSteps objects;
    @Getter
    private final Browser browser;

    public FrontSteps(ObjectSteps objects, BrowserFactory browserFactory) {
        this.objects = objects;
        this.browser = browserFactory.createBrowser();
    }

    @BeforeAll
    public static void init() {
    }

    @Given(THAT + Guard.GUARD + "browser navigate to " + QUOTED_CONTENT + STEPS_PATTERN + "$")
    public void we_navigate_to_url(Guard guard, String url, BrowserAssertion browserAssertion) {
        guard.in(objects, () -> {
            browser.get(url);
            assert_element_on_page(browserAssertion);
        });
    }

    @Then(Guard.GUARD + "browser url is " + QUOTED_CONTENT + "( within " + A_DURATION + ")?" + "$")
    public void we_wait_for_an_url(Guard guard, String url, Integer timeout) {
        guard.in(objects, () -> assertThat(browser.waitForPage(url, timeout)).isTrue());
    }

    @When(THAT + GUARD + A_USER + "perform a " + Action.REGEX + "( with \\( *([^)]+?) *\\))? on " + QUOTED_CONTENT + STEPS_PATTERN + "$")
    public void perform_action(Guard guard, Action action, String parametersString, String selector, BrowserAssertion browserAssertion) {
        guard.in(objects, () -> {
            String[] parameters;
            if (parametersString != null) {
                parameters = Arrays.stream(parametersString.split(",")).map(s -> s.replaceAll("^\"|\"$", "")).toArray(String[]::new);
            } else {
                parameters = new String[0];
            }
            List<HTMLElement> htmlElements = browser.find(selector);
            if (htmlElements == null || htmlElements.isEmpty()) {
                throw new AssertionError("No HTML elements found for selector " + selector);
            }
            htmlElements.forEach(htmlElement -> htmlElement.performAction(action, parameters));
            assert_element_on_page(browserAssertion);
        });
    }

    @Then(THAT + Guard.GUARD + "the page" + BrowserAssertion.STEPS_PATTERN + HTMLElementAssertion.STEPS_PATTERN + "$")
    public void the_page_contains(Guard guard, BrowserAssertion browserAssertion, HTMLElementAssertion htmlElementAssertion) {
        guard.in(objects, () -> {
            assert_element_on_page(browserAssertion);
            assert_attributes_on_element(browserAssertion, htmlElementAssertion);
        });
    }

    private void assert_attributes_on_element(BrowserAssertion browserAssertion, HTMLElementAssertion htmlElementAssertion) {
        if (htmlElementAssertion.getAttributes() != null && !htmlElementAssertion.getAttributes().isEmpty()) {
            browser.find(browserAssertion.getSelector())
                    .forEach(htmlElement -> assertThat(htmlElement.getAttributes().entrySet().containsAll(htmlElementAssertion.getAttributes().entrySet())).isTrue());
        }

        if (htmlElementAssertion.getStyles() != null && !htmlElementAssertion.getStyles().isEmpty()) {
            browser.find(browserAssertion.getSelector())
                    .forEach(htmlElement -> assertThat(htmlElementAssertion.getStyles().entrySet().stream()
                            .allMatch(entry -> entry.getValue().equals(htmlElement.getStyleValue(entry.getKey())))).isTrue());
        }
    }

    private void assert_element_on_page(BrowserAssertion browserAssertion) {
        if (StringUtils.isNotBlank(browserAssertion.getSelector())) {
            assertThat(browser.waitForElement(browserAssertion.getSelector(), browserAssertion.isVisible(), browserAssertion.getTimeoutMs())).isTrue();
        }
    }


    @After
    public void after() {
        browser.close();
    }
}
