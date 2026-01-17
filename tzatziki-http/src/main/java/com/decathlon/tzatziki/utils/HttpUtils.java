package com.decathlon.tzatziki.utils;

import com.decathlon.tzatziki.steps.HttpSteps;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import java.util.*;
import java.util.function.Function;

import static com.decathlon.tzatziki.steps.HttpSteps.MOCKED_PATHS;
import static com.decathlon.tzatziki.steps.HttpSteps.wireMockServer;
import static com.decathlon.tzatziki.utils.Comparison.CONTAINS;

public class HttpUtils {
    private static final List<MappingBuilder> PERSISTENT_MOCKS = new ArrayList<>();

    public static String url() {
        return HttpWiremockUtils.url();
    }

    public static String target(String path) {
        return HttpWiremockUtils.target(path);
    }

    /**
     * Mocks an HTTP interaction for testing purposes.
     * <p>
     * This method registers a mock HTTP interaction with the WireMock server using the provided
     * {@link Interaction} definition, {@link Comparison} strategy, and an optional response transformer.
     * The mock will not persist across WireMock server resets.
     * </p>
     *
     * @param interaction the HTTP interaction to mock, including request and response definitions
     * @param comparison  the comparison strategy to use when matching incoming request bodies
     * @param transformer a function to dynamically generate a response based on the incoming request,
     *                    or {@code null} for static responses
     * @see #mockInteraction(Interaction, Comparison, Function, boolean)
     */
    public static void mockInteraction(Interaction interaction, Comparison comparison, 
                                       Function<Interaction.Request, Interaction.Response> transformer) {
        mockInteraction(interaction, comparison, transformer, false);
    }

    /**
     * Same as {@link #mockInteraction(Interaction, Comparison, Function)}, but the mock persists across WireMock server resets.
     *
     * @param interaction the HTTP interaction to mock
     * @param comparison  the comparison strategy for matching request bodies
     * @param transformer a function to generate a response based on the request, or {@code null} for static responses
     */
    public static void persistentMockInteraction(Interaction interaction, Comparison comparison,
                                                 Function<Interaction.Request, Interaction.Response> transformer) {
        mockInteraction(interaction, comparison, transformer, true);
    }
    
    private static void mockInteraction(Interaction interaction, Comparison comparison, Function<Interaction.Request,
            Interaction.Response> transformer, boolean persistsAcrossResets) {
        ResponseDefinitionBuilder responseDefinition = interaction.response.get(0).toResponseDefinitionBuilder(null,
                HttpWiremockUtils.match(interaction.request.path));
        if (transformer != null) {
            responseDefinition.withTransformer("custom-callback-transformer", "callback", transformer);
        }

        MappingBuilder request = interaction.request.toMappingBuilder(null,
                HttpWiremockUtils.match(interaction.request.path), comparison).willReturn(responseDefinition);
        wireMockServer.stubFor(request);
        if(persistsAcrossResets) {
            PERSISTENT_MOCKS.add(request);
        }
    }

    /**
     * Mocks a simple HTTP request for testing purposes.
     * <p>
     * This is a simpler version of {@link #mockInteraction(Interaction, Comparison, Function)}.
     * It creates and registers a mock HTTP interaction with the WireMock server using only
     * the specified path, HTTP method, status, and optional JSON response body.
     * </p>
     *
     * @param path         the request path to mock
     * @param method       the HTTP method to mock (e.g., GET, POST)
     * @param status       the HTTP status code to return in the response
     * @param responseBody the JSON response body to return, or {@code null} for no body
     */
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
        PERSISTENT_MOCKS.forEach(wireMockServer::stubFor);
    }

    public static Integer localPort() {
        return HttpSteps.localPort;
    }
}
