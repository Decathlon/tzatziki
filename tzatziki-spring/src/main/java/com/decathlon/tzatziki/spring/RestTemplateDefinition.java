package com.decathlon.tzatziki.spring;

import com.decathlon.tzatziki.utils.Fields;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;


@ConditionalOnClass(RestTemplate.class)
@Component
public class RestTemplateDefinition implements HttpInterceptorDefinition<RestTemplate> {
    @Override
    public RestTemplate rewrite(RestTemplate webClient) {
        ClientHttpRequestFactory requestFactory = Fields.getValue(webClient, "requestFactory");
        Fields.setValue(webClient, "requestFactory", new ClientHttpRequestFactory() {
            @SneakyThrows
            @Override
            public @NotNull
            ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
                return requestFactory.createRequest(HttpInterceptor.remap(uri), httpMethod);
            }
        });
        return webClient;
    }
}
