package com.decathlon.tzatziki.steps_everything;

import com.decathlon.tzatziki.config.McpClientConfiguration;
import io.cucumber.java.Before;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
public class McpEverythingServerSteps {
    private static GenericContainer<?> mcpContainer;

    @Before(order = -1)
    public void before() {
        if (mcpContainer == null || !mcpContainer.isRunning()) {
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

            McpClientConfiguration.elicitationHandler = request -> Mono.just(McpSchema.ElicitResult.builder()
                    .message(McpSchema.ElicitResult.Action.ACCEPT)
                    .content(Map.of("message", "elicitation response"))
                    .build());

            McpClientConfiguration.samplingHandler = request ->
                    Mono.just(new McpSchema.CreateMessageResult(McpSchema.Role.ASSISTANT, request.messages().get(0).content(), "test-model",
                            McpSchema.CreateMessageResult.StopReason.END_TURN));

            McpClientConfiguration.roots = List.of(new McpSchema.Root("file:///test/path", "test-root"));
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
