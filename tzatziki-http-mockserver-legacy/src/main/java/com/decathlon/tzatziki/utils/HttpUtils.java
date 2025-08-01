package com.decathlon.tzatziki.utils;

import org.mockserver.model.HttpRequest;
import org.mockserver.verify.VerificationTimes;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.decathlon.tzatziki.utils.MockFaster.add_mock;

public class HttpUtils {
    public static String url() {
        return MockFaster.url();
    }

    public static String target(String path) {
        return MockFaster.target(path);
    }

    public static void mockInteraction(Interaction interaction, Comparison comparison, Function<Interaction.Request, Interaction.Response> transformer) {
        HttpRequest httpRequestIn = interaction.request.toHttpRequestIn(null, MockFaster.match(interaction.request.path), false);
        add_mock(httpRequestIn, request -> {
            Interaction.Response response = transformer != null ? transformer.apply(Interaction.Request.fromHttpRequest(request)) :
                    interaction.response.get(0);
            return response.toHttpResponseIn(null, null);
        }, comparison);
    }

    public static void verify(Interaction.Request request, Integer times) {
        MockFaster.verify(request.toHttpRequestIn(null, MockFaster.match(request.path), false), VerificationTimes.exactly(times));
    }

    public static void mockSimpleRequest(String path, Method method, String status, String responseBody) {
        Interaction.Response.ResponseBuilder responseBuilder = Interaction.Response.builder().status(status);

        if (responseBody != null) {
            responseBuilder
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(Interaction.Body.builder().payload(responseBody).build());
        }

        Interaction interaction = Interaction.builder()
                .request(Interaction.Request.builder().path(path).method(method).build())
                .response(List.of(responseBuilder.build()))
                .build();

        HttpUtils.mockInteraction(interaction, Comparison.CONTAINS, null);
    }

    public static void reset() {
        MockFaster.reset();
    }

    public static Integer localPort() {
        return MockFaster.localPort();
    }
}
