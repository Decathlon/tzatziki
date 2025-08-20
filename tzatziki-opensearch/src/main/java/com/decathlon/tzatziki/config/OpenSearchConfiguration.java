package com.decathlon.tzatziki.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;

import java.net.URI;

@Slf4j
public class OpenSearchConfiguration {

    private final String opensearchHost;
    @Getter
    private OpenSearchClient openSearchClient;
    @Getter
    private RestClient restClient;

    public OpenSearchConfiguration(String opensearchHost) {
        this.opensearchHost = opensearchHost;
        initializeClients();
    }

    private void initializeClients() {
        URI url;
        try {
            url = URI.create(opensearchHost);
        } catch (IllegalArgumentException e) {
            log.error("Malformed OpenSearch host URI: {}", opensearchHost, e);
            throw new RuntimeException("Failed to initialize OpenSearch clients due to malformed host URI: " + opensearchHost, e);
        }
        restClient =  RestClient.builder(new HttpHost(url.getScheme(), url.getHost(), url.getPort())).build();
        JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(new ObjectMapper());
        OpenSearchTransport transport = new RestClientTransport(restClient, jsonpMapper);
        this.openSearchClient = new OpenSearchClient(transport);
    }

    public void close() {
        try {
            if (openSearchClient != null) {
                openSearchClient._transport().close();
            }
            if (restClient != null) {
                restClient.close();
            }
        } catch (Exception e) {
            log.error("Error closing OpenSearch clients", e);
        }
    }
}
