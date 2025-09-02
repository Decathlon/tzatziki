package com.decathlon.tzatziki.spring;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@Component
@Conditional(RestTemplateBuilderCondition.class)
public class RestTemplateBuilderDefinition implements HttpInterceptorDefinition<Object> {

    private static final String SPRING_BOOT_3_PACKAGE = "org.springframework.boot.web.client.RestTemplateBuilder";
    private static final String SPRING_BOOT_4_PACKAGE = "org.springframework.boot.restclient.RestTemplateBuilder";

    private final Class<?> restTemplateBuilderClass;
    private final Method additionalInterceptorsMethod;

    public RestTemplateBuilderDefinition() {
        this.restTemplateBuilderClass = loadRestTemplateBuilderClass();
        try {
            this.additionalInterceptorsMethod = restTemplateBuilderClass.getMethod("additionalInterceptors", ClientHttpRequestInterceptor[].class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to find additionalInterceptors method on RestTemplateBuilder", e);
        }
    }

    public static Class<?> loadRestTemplateBuilderClass() {
        // Try Spring Boot 3 location first
        try {
            return Class.forName(SPRING_BOOT_3_PACKAGE);
        } catch (ClassNotFoundException e) {
            // Fall back to Spring Boot 4 location
            try {
                return Class.forName(SPRING_BOOT_4_PACKAGE);
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException("RestTemplateBuilder not found in either Spring Boot 3 or Spring Boot 4 package", ex);
            }
        }
    }

    @Override
    public Object rewrite(Object restTemplateBuilder) {
        if (!restTemplateBuilderClass.isInstance(restTemplateBuilder)) {
            throw new IllegalArgumentException("Expected RestTemplateBuilder instance, got: " + restTemplateBuilder.getClass());
        }

        try {
            return additionalInterceptorsMethod.invoke(restTemplateBuilder, (Object) new ClientHttpRequestInterceptor[]{
                    new ClientHttpRequestInterceptor() {
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
                    }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to add interceptors to RestTemplateBuilder", e);
        }
    }
}
