package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.steps.HttpSteps;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.decathlon.tzatziki.steps.HttpSteps.MOCKED_PATHS;
import static com.decathlon.tzatziki.steps.HttpSteps.wireMockServer;
import static com.decathlon.tzatziki.utils.Comparison.CONTAINS;

public class HttpUtils {
    public static String url() {
        return HttpWiremockUtils.url();
    }

    public static String target(String path) {
        return HttpWiremockUtils.target(path);
    }

    public static void mockInteraction(Interaction interaction, Comparison comparison, Function<Interaction.Request, Interaction.Response> transformer) {
        ResponseDefinitionBuilder responseDefinition = interaction.response.get(0).toResponseDefinitionBuilder(null, HttpWiremockUtils.match(interaction.request.path));
        if (transformer != null) {
            responseDefinition.withTransformer("custom-callback-transformer", "callback", transformer);
        }

        MappingBuilder request = interaction.request.toMappingBuilder(null, HttpWiremockUtils.match(interaction.request.path), comparison).willReturn(responseDefinition);
        wireMockServer.stubFor(request);
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

    public static void verify(Interaction.Request request, Integer times) {
        HttpSteps.assertHasReceived(request.toRequestPatternBuilder(null, HttpWiremockUtils.match(request.path), CONTAINS), times);
    }

    public static void reset() {
        wireMockServer.resetAll();
        MOCKED_PATHS.clear();
    }

    public static Integer localPort() {
        return HttpSteps.localPort;
    }
}
