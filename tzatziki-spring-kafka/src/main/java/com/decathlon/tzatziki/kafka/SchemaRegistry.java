package com.decathlon.tzatziki.kafka;

import com.decathlon.tzatziki.steps.HttpSteps;
import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.get;

public class SchemaRegistry {


    public static String endpoint = "/schemaRegistry/";

    public static void initialize() {
        HttpSteps.wireMockServer.stubFor(post(
                WireMock.urlPathMatching(endpoint + "subjects/.+/versions"))
                .willReturn(aResponse()
                        .withTransformers("schema-registry-response-transformer")));


        HttpSteps.wireMockServer.stubFor(get(
                WireMock.urlPathMatching(endpoint + "schemas/ids/(.+)"))
                .willReturn(aResponse()
                        .withTransformers("schema-registry-response-transformer")));
    }
}
