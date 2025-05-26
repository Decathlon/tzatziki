package com.decathlon.tzatziki.utils;

import org.mockserver.model.HttpRequest;

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


}
