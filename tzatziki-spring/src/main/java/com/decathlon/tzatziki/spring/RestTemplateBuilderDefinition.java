package com.decathlon.tzatziki.spring;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Proxy;


@ConditionalOnClass(RestTemplateBuilder.class)
@Component
public class RestTemplateBuilderDefinition implements HttpInterceptorDefinition<RestTemplateBuilder> {
    @Override
    public RestTemplateBuilder rewrite(RestTemplateBuilder webClient) {
        return webClient.additionalInterceptors(new ClientHttpRequestInterceptor() {
            @Override
            public @NotNull ClientHttpResponse intercept(
                    @NotNull HttpRequest request,
                    byte @NotNull [] body,
                    @NotNull ClientHttpRequestExecution execution) throws IOException {
                HttpRequest proxiedHttpRequest = (HttpRequest) Proxy.newProxyInstance(
                        request.getClass().getClassLoader(),
                        new Class[]{HttpRequest.class},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "getURI" -> HttpInterceptor.remap(request.getURI());
                            default -> method.invoke(request, args);
                        });
                return execution.execute(proxiedHttpRequest, body);
            }
        });
    }
}
