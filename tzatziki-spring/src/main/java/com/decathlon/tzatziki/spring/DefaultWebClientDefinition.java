package com.decathlon.tzatziki.spring;

import com.decathlon.tzatziki.utils.Fields;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Function;


@ConditionalOnClass(WebClient.class)
@Component
public class DefaultWebClientDefinition implements HttpInterceptorDefinition<WebClient> {
    @Override
    public WebClient rewrite(WebClient webClient) {
        if (!webClient.getClass().getName().equals("org.springframework.web.reactive.function.client.DefaultWebClient")) {
            return webClient;
        }
        ExchangeFunction exchangeFunction = Fields.getValue(webClient, "exchangeFunction");
        if (Fields.hasField(exchangeFunction, "connector")) {
            // we assume that this webClient was created by a builder that we already intercepted
            ClientHttpConnector clientHttpConnector = Fields.getValue(exchangeFunction, "connector");
            Fields.setValue(exchangeFunction, "connector", new ClientHttpConnector() {
                @Override
                @SneakyThrows
                public @NotNull Mono<ClientHttpResponse> connect(
                        @NotNull HttpMethod method,
                        @NotNull URI uri,
                        @NotNull Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {
                    return clientHttpConnector.connect(method, HttpInterceptor.remap(uri), requestCallback);
                }
            });
        }
        return webClient;
    }
}
