package com.decathlon.tzatziki.config;

import com.decathlon.tzatziki.steps.McpSteps;
import com.decathlon.tzatziki.utils.McpEvent;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

@Slf4j
public class McpClientConfiguration {

    @Setter
    private static McpClientTransport mcpClientTransport;
    @Getter
    private McpAsyncClient mcpAsyncClient;

    @Setter
    private static Function<McpSchema.ElicitRequest, Mono<McpSchema.ElicitResult>> elicitationHandler;
    @Setter
    private static Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>> samplingHandler;
    @Setter
    private static List<McpSchema.Root> roots;

    public McpClientConfiguration() {
        initializeClient();
    }

    private void initializeClient() {
        McpSchema.ClientCapabilities.Builder clientCapabilitiesBuilder = McpSchema.ClientCapabilities.builder();

        McpClient.AsyncSpec asyncSpec = McpClient.async(mcpClientTransport);
        asyncSpec.toolsChangeConsumer(tools -> {
                    McpSteps.getMcpEvents().add(McpEvent.fromToolsChange(tools));
                    return Mono.empty();
                })
                .resourcesUpdateConsumer(resources -> {
                    McpSteps.getMcpEvents().add(McpEvent.fromResourcesUpdate(resources));
                    return Mono.empty();
                })
                .resourcesChangeConsumer(resources -> {
                    McpSteps.getMcpEvents().add(McpEvent.fromResourcesChange(resources));
                    return Mono.empty();
                })
                .promptsChangeConsumer(prompts -> {
                    McpSteps.getMcpEvents().add(McpEvent.fromPromptsChange(prompts));
                    return Mono.empty();
                })
                .progressConsumer(progressNotification -> {
                    McpSteps.getMcpEvents().add(McpEvent.fromProgressNotification(progressNotification));
                    return Mono.empty();
                })
                .loggingConsumer(loggingMessageNotification -> {
                    McpSteps.getMcpEvents().add(McpEvent.fromLoggingNotification(loggingMessageNotification));
                    return Mono.empty();
                });

        if (elicitationHandler != null) {
            asyncSpec.elicitation(elicitationHandler);
            clientCapabilitiesBuilder.elicitation();
        }
        if (samplingHandler != null) {
            asyncSpec.sampling(samplingHandler);
            clientCapabilitiesBuilder.sampling();
        }
        if (roots != null && !roots.isEmpty()) {
            asyncSpec.roots(roots);
            clientCapabilitiesBuilder.roots(true);
        }
        asyncSpec.capabilities(clientCapabilitiesBuilder.build());
        mcpAsyncClient = asyncSpec.build();
        mcpAsyncClient.initialize().block();
    }


    public void close() {
        mcpAsyncClient.close();
    }
}