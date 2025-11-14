package com.decathlon.tzatziki.config;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class McpClientConfiguration {

    public static McpClientTransport mcpClientTransport;
    @Getter
    private McpAsyncClient mcpAsyncClient;

    public McpClientConfiguration() {
        initializeClient();
    }

    private void initializeClient() {
        mcpAsyncClient = McpClient.async(mcpClientTransport)
                .build();

        mcpAsyncClient.initialize();
    }

    public void close() {
        mcpAsyncClient.close();
    }
}