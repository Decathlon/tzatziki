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
    public RestTemplate rewrite(RestTemplate restTemplate) {
        ClientHttpRequestFactory requestFactory = Fields.getValue(restTemplate, "requestFactory");
        Fields.setValue(restTemplate, "requestFactory", new ClientHttpRequestFactory() {
            @Override
            public @NotNull
            ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
                try {
                    return requestFactory.createRequest(HttpInterceptor.remap(uri), httpMethod);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
        });
        return restTemplate;
    }
}
