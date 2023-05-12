package com.decathlon.tzatziki.spring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Proxy;


@ConditionalOnClass(WebClient.Builder.class)
@Component
public class WebClientBuilderDefinition implements HttpInterceptorDefinition<WebClient.Builder> {
    @Override
    public WebClient.Builder rewrite(WebClient.Builder webClientBuilder) {
        return webClientBuilder.filter((request, next) -> {
            ClientRequest proxiedClientRequest = (ClientRequest) Proxy.newProxyInstance(
                    ClientRequest.class.getClassLoader(),
                    new Class[]{ClientRequest.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "url" -> HttpInterceptor.remap(request.url());
                        default -> method.invoke(request, args);
                    });
            return next.exchange(proxiedClientRequest);
        });
    }
}
