package com.decathlon.tzatziki.utils;

import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.util.function.UnaryOperator;

import static com.decathlon.tzatziki.utils.MockFaster.add_mock;

public class HttpUtils {
    public static String url() {
        return MockFaster.url();
    }

    public static String target(String path) {
        return MockFaster.target(path);
    }

    public static void mockInteraction(Interaction interaction, Comparison comparison, Object transformer) {
        HttpRequest httpRequestIn = interaction.request.toHttpRequestIn(null, MockFaster.match(interaction.request.path), false);

        if (transformer instanceof UnaryOperator callback) {
            add_mock(httpRequestIn, request -> (HttpResponse) callback.apply(request), comparison);
        } else {
            add_mock(httpRequestIn, request -> interaction.response.get(0).toHttpResponseIn(null, null), comparison);
        }
    }


}
