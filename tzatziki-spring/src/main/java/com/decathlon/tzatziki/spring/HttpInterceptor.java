package com.decathlon.tzatziki.spring;

import com.decathlon.tzatziki.utils.Fields;
import com.decathlon.tzatziki.utils.MockFaster;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.*;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;

@ConditionalOnWebApplication
@Aspect
@Component
@Slf4j
public class HttpInterceptor {

    private static boolean enabled = true;

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    @Around("@annotation(org.springframework.context.annotation.Bean) && !within(is(FinalType))")
    public Object beanCreation(ProceedingJoinPoint joinPoint) throws Throwable {
        Object bean = joinPoint.proceed();
        if (enabled) {
            if (bean instanceof RestTemplate restTemplate) {
                ClientHttpRequestFactory requestFactory = Fields.getValue(restTemplate, "requestFactory");
                Fields.setValue(restTemplate, "requestFactory", new ClientHttpRequestFactory() {
                    @SneakyThrows
                    @Override
                    public @NotNull
                    ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
                        return requestFactory.createRequest(remap(uri), httpMethod);
                    }
                });
                return restTemplate;
            } else if (bean instanceof RestTemplateBuilder restTemplateBuilder) {
                return restTemplateBuilder.additionalInterceptors(new ClientHttpRequestInterceptor() {
                    @Override
                    public @NotNull ClientHttpResponse intercept(
                            @NotNull HttpRequest request,
                            byte @NotNull [] body,
                            @NotNull ClientHttpRequestExecution execution) throws IOException {
                        HttpRequest proxiedHttpRequest = (HttpRequest) Proxy.newProxyInstance(
                                request.getClass().getClassLoader(),
                                new Class[]{HttpRequest.class},
                                (proxy, method, args) -> switch (method.getName()) {
                                    case "getURI" -> remap(request.getURI());
                                    default -> method.invoke(request, args);
                                });
                        return execution.execute(proxiedHttpRequest, body);
                    }
                });
            } else if (bean instanceof WebClient webClient && bean.getClass().getName().equals("org.springframework.web.reactive.function.client.DefaultWebClient")) {
                ExchangeFunction exchangeFunction = Fields.getValue(bean, "exchangeFunction");
                if (Fields.hasField(exchangeFunction, "connector")) {
                    // we assume that this webClient was created by a builder that we already intercepted
                    ClientHttpConnector clientHttpConnector = Fields.getValue(exchangeFunction, "connector");
                    Fields.setValue(exchangeFunction, "connector", new ClientHttpConnector() {
                        @Override
                        @SneakyThrows
                        public @NotNull Mono<org.springframework.http.client.reactive.ClientHttpResponse> connect(
                                @NotNull HttpMethod method,
                                @NotNull URI uri,
                                @NotNull Function<? super org.springframework.http.client.reactive.ClientHttpRequest, Mono<Void>> requestCallback) {
                            return clientHttpConnector.connect(method, remap(uri), requestCallback);
                        }
                    });
                }
                return webClient;
            } else if (bean instanceof WebClient.Builder webClientBuilder) {
                return webClientBuilder.filter((request, next) -> {
                    ClientRequest proxiedClientRequest = (ClientRequest) Proxy.newProxyInstance(
                            ClientRequest.class.getClassLoader(),
                            new Class[]{ClientRequest.class},
                            (proxy, method, args) -> switch (method.getName()) {
                                case "url" -> remap(request.url());
                                default -> method.invoke(request, args);
                            });
                    return next.exchange(proxiedClientRequest);
                });
            }
        }
        return bean;
    }

    @NotNull
    private URI remap(URI uri) throws URISyntaxException {
        if (enabled) {
            return new URI(MockFaster.target(uri.toString()));
        }
        return uri;
    }
}
