package com.decathlon.tzatziki.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import java.net.URI;

@Slf4j
public class OpenSearchConfiguration {

    private final String opensearchHost;
    @Getter
    private OpenSearchClient openSearchClient;

    public OpenSearchConfiguration(String opensearchHost) {
        this.opensearchHost = opensearchHost;
        initializeClients();
    }

    private void initializeClients() {
        final URI url;
        try {
            url = URI.create(opensearchHost);
        } catch (IllegalArgumentException e) {
            log.error("Malformed OpenSearch host URI: {}", opensearchHost, e);
            throw new IllegalArgumentException(
                    "Failed to initialize OpenSearch client due to malformed host URI: " + opensearchHost, e
            );
        }

        HttpHost host = new HttpHost(url.getScheme(), url.getHost(), url.getPort());

        JacksonJsonpMapper mapper = new JacksonJsonpMapper(new ObjectMapper());

        this.openSearchClient = new OpenSearchClient(ApacheHttpClient5TransportBuilder
                .builder(host)
                .setMapper(mapper).build());
    }

    public void close() {
        try {
            if (openSearchClient != null) {
                openSearchClient._transport().close();
            }
        } catch (Exception e) {
            log.error("Error closing OpenSearch client", e);
        }
    }
}