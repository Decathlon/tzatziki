package com.decathlon.tzatziki.app.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;

import static com.decathlon.tzatziki.utils.Unchecked.unchecked;

@RestController
public class HelloController {

    private static final URI remoteBackend = unchecked(() -> new URI("http://backend/greeting"));

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Autowired
    private WebClient webClient;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @GetMapping("/hello")
    public ResponseEntity<String> hello() {
        return ResponseEntity.ok("Hello world!");
    }

    @GetMapping("/rest-template-remote-hello")
    public ResponseEntity<String> restTemplateRemoteHello() throws URISyntaxException {
        return ResponseEntity.ok(restTemplate.getForObject(remoteBackend, String.class));
    }

    @GetMapping("/rest-template-builder-remote-hello")
    public ResponseEntity<String> restTemplateBuilderRemoteHello() throws URISyntaxException {
        return ResponseEntity.ok(restTemplateBuilder.build().getForObject(remoteBackend, String.class));
    }

    @GetMapping("/web-client-remote-hello")
    public Mono<ResponseEntity<String>> webClientRemoteHello() throws URISyntaxException {
        return webClient.get().uri(remoteBackend).retrieve().toEntity(String.class);
    }

    @GetMapping("/web-client-builder-remote-hello")
    public Mono<ResponseEntity<String>> webClientBuilderRemoteHello() throws URISyntaxException {
        return webClientBuilder.build().get().uri(remoteBackend).retrieve().toEntity(String.class);
    }
}
