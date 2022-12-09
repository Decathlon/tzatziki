package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Interaction;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import lombok.RequiredArgsConstructor;

import java.util.stream.IntStream;

@RequiredArgsConstructor
public class LocalSteps {
    private final HttpSteps httpSteps;

    static {
        Interaction.printResponses = true;
    }

    @Before
    public void before() {}

    @Given("^we add (\\d+)-(\\d+) mocks for id endpoint$")
    public void mockIdEndpointAsSeveralMocks(int startId, int endId) {
        IntStream.range(startId, endId + 1).forEach(idx -> httpSteps.url_is_mocked_as(Guard.always(), "http://backend/" + idx, Comparison.CONTAINS, """
                request:
                    method: GET
                response:
                    headers:
                        Content-Type: application/json
                    body:
                        payload: Hello %d
                """.formatted(idx)));
    }
}
