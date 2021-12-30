package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Interaction;
import io.cucumber.java.Before;
import io.cucumber.java.en.When;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.decathlon.tzatziki.utils.Guard.always;
import static com.decathlon.tzatziki.utils.Method.GET;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static io.semla.util.Unchecked.unchecked;

public class LocalSteps {

    static {
        Interaction.printResponses = true;
    }

    private final HttpSteps httpSteps;

    public LocalSteps(HttpSteps httpSteps) {
        this.httpSteps = httpSteps;
    }

    @Before
    public void before() {}

    @When(THAT + A_USER + "gets? " + QUOTED_CONTENT + " " + A_DURATION + " later")
    public void that_we_get_100ms_later(String path, int sleep) {
        CompletableFuture.runAsync(() -> {
            unchecked(() -> TimeUnit.MILLISECONDS.sleep(sleep));
            httpSteps.call(always(), "we", GET, path);
        });
    }
}
