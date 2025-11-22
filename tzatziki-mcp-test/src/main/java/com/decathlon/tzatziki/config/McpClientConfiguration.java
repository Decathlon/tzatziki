package com.decathlon.tzatziki.config;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;

@Slf4j
public class McpClientConfiguration {

    public static McpClientTransport mcpClientTransport;
    @Getter
    private McpAsyncClient mcpAsyncClient;

    public McpClientConfiguration() {
        initializeClient();
    }

    private void initializeClient() {
        Function<McpSchema.ElicitRequest, Mono<McpSchema.ElicitResult>> elicitationHandler = request -> Mono.just(McpSchema.ElicitResult.builder()
                .message(McpSchema.ElicitResult.Action.ACCEPT)
                .content(Map.of("message", "Eclitation response"))
                .build());

        Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>> samplingHandler = request ->
                Mono.just(new McpSchema.CreateMessageResult(McpSchema.Role.ASSISTANT, request.messages().get(0).content(), "test-model",
                        McpSchema.CreateMessageResult.StopReason.END_TURN));

        McpSchema.ClientCapabilities capabilities = McpSchema.ClientCapabilities.builder()
                .roots(true)
                .elicitation()
                .sampling()
                .build();

        mcpAsyncClient = McpClient.async(mcpClientTransport).sampling(samplingHandler).elicitation(elicitationHandler)
                .roots(new McpSchema.Root("file:///test/path", "test-root"))
                .capabilities(capabilities)
                .build();


        mcpAsyncClient.initialize().block();
    }

    public void close() {
        mcpAsyncClient.close();
    }
}