package com.decathlon.tzatziki.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Slf4j
@Configuration
public class OpenSearchConfiguration {

    @Value("${opensearch.host}")
    private String opensearchHost;

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RestClient restClient() {
        URI url = URI.create(opensearchHost);
        return RestClient.builder(new HttpHost(url.getScheme(), url.getHost(), url.getPort())).build();
    }

    @Bean
    public OpenSearchClient openSearchClient(RestClient restClient, ObjectMapper objectMapper) {
        JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(objectMapper);
        OpenSearchTransport transport = new RestClientTransport(restClient, jsonpMapper);
        return new OpenSearchClient(transport);
    }
}
