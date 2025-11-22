package com.decathlon.tzatziki.steps_everything;

import com.decathlon.tzatziki.config.McpClientConfiguration;
import io.cucumber.java.Before;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@Slf4j
public class McpEverythingServerSteps {
    private static GenericContainer<?> mcpContainer;

    @Before(order = -1)
    public void before() throws InterruptedException {
        if (mcpContainer == null || !mcpContainer.isRunning()) {
            log.info("Starting MCP Everything Server container...");

            mcpContainer = new GenericContainer<>("docker.io/tzolov/mcp-everything-server:v3")
                    .withCommand("node dist/index.js sse")
                    .withLogConsumer(outputFrame -> System.out.println(outputFrame.getUtf8String()))
                    .withExposedPorts(3001)
                    .waitingFor(Wait.forHttp("/").forStatusCode(404));

            mcpContainer.start();

            log.info("MCP Everything Server container started");

            int port = mcpContainer.getMappedPort(3001);
            String host = "http://" + mcpContainer.getHost() + ":" + port;

            McpClientConfiguration.mcpClientTransport = HttpClientSseClientTransport.builder(host).build();
        }

    }

    public static void stopContainer() {
        if (mcpContainer != null && mcpContainer.isRunning()) {
            log.info("Stopping MCP Everything Server container...");
            mcpContainer.stop();
        }
    }

    // Ensure container and process are stopped on JVM shutdown
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(McpEverythingServerSteps::stopContainer));
    }
}
