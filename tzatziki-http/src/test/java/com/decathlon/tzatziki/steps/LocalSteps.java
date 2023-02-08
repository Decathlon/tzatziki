package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.Asserts;
import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Interaction;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static com.decathlon.tzatziki.utils.MockFaster.target;
import static com.decathlon.tzatziki.utils.Patterns.QUOTED_CONTENT;
import static com.decathlon.tzatziki.utils.Unchecked.unchecked;
import static io.restassured.RestAssured.given;

@RequiredArgsConstructor
public class LocalSteps {
    private final HttpSteps httpSteps;
    private final ObjectSteps objects;

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

    @Then("getting (?:on )?" + QUOTED_CONTENT + " four times in parallel returns:$")
    public void sendInParallel(String path, String content) {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        List<CompletableFuture<Interaction.Response>> responsesAsync = List.of(
                CompletableFuture.supplyAsync(() -> Interaction.Response.fromResponse(Interaction.Request.builder().build().send(given(), target(path), objects)), executor),
                CompletableFuture.supplyAsync(() -> Interaction.Response.fromResponse(Interaction.Request.builder().build().send(given(), target(path), objects)), executor),
                CompletableFuture.supplyAsync(() -> Interaction.Response.fromResponse(Interaction.Request.builder().build().send(given(), target(path), objects)), executor),
                CompletableFuture.supplyAsync(() -> Interaction.Response.fromResponse(Interaction.Request.builder().build().send(given(), target(path), objects)), executor)
        );

        Asserts.contains(responsesAsync.stream().map(future -> unchecked(() -> future.get())).map(response -> response.body.payload).toList(), objects.resolve(content));
    }
}
