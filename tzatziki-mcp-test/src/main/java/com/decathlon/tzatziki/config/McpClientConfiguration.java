package com.decathlon.tzatziki.config;

import com.decathlon.tzatziki.steps.McpSteps;
import com.decathlon.tzatziki.utils.McpEvent;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

@Slf4j
public class McpClientConfiguration {

    public static McpClientTransport mcpClientTransport;
    @Getter
    private McpAsyncClient mcpAsyncClient;

    public static Function<McpSchema.ElicitRequest, Mono<McpSchema.ElicitResult>> elicitationHandler;
    public static Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>> samplingHandler;
    public static List<McpSchema.Root> roots;

    public McpClientConfiguration() {
        initializeClient();
    }

    private void initializeClient() {
        McpSchema.ClientCapabilities.Builder clientCapabilitiesBuilder = McpSchema.ClientCapabilities.builder();

        McpClient.AsyncSpec asyncSpec = McpClient.async(mcpClientTransport);
        asyncSpec.toolsChangeConsumer(tools -> {
                    McpSteps.mcpEvents.add(McpEvent.fromToolsChange(tools));
                    return Mono.empty();
                })
                .resourcesUpdateConsumer(resources -> {
                    McpSteps.mcpEvents.add(McpEvent.fromResourcesUpdate(resources));
                    return Mono.empty();
                })
                .resourcesChangeConsumer(resources -> {
                    McpSteps.mcpEvents.add(McpEvent.fromResourcesChange(resources));
                    return Mono.empty();
                })
                .promptsChangeConsumer(prompts -> {
                    McpSteps.mcpEvents.add(McpEvent.fromPromptsChange(prompts));
                    return Mono.empty();
                })
                .progressConsumer(progressNotification -> {
                    McpSteps.mcpEvents.add(McpEvent.fromProgressNotification(progressNotification));
                    return Mono.empty();
                })
                .loggingConsumer(loggingMessageNotification -> {
                    McpSteps.mcpEvents.add(McpEvent.fromLoggingNotification(loggingMessageNotification));
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