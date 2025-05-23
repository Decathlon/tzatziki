package com.decathlon.tzatziki.utils;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import static com.decathlon.tzatziki.steps.HttpSteps.wireMockServer;

public class HttpUtils {
    public static String url() {
        return HttpWiremockUtils.url();
    }

    public static String target(String path) {
        return HttpWiremockUtils.target(path);
    }

    public static void mockInteraction(Interaction interaction, Comparison comparison, Object transformer) {
        ResponseDefinitionBuilder responseDefinition = interaction.response.get(0).toResponseDefinitionBuilder(null, HttpWiremockUtils.match(interaction.request.path));
        if (transformer instanceof String transformerName) {
            responseDefinition.withTransformers(transformerName);
        }

        MappingBuilder request = interaction.request.toMappingBuilder(null, HttpWiremockUtils.match(interaction.request.path), comparison).willReturn(responseDefinition);
        wireMockServer.stubFor(request);
    }
}
