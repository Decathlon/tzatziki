package com.decathlon.tzatziki.utils;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class McpEvent {

    public enum EventType {
        TOOLS_CHANGE,
        RESOURCES_CHANGE,
        RESOURCES_UPDATE,
        PROMPTS_CHANGE,
        PROGRESS,
        LOGGING
    }

    private EventType type;
    private Instant timestamp;
    private Object payload;

    public static McpEvent fromToolsChange(List<McpSchema.Tool> tools) {
        return McpEvent.builder()
                .type(EventType.TOOLS_CHANGE)
                .timestamp(Instant.now())
                .payload(tools)
                .build();
    }

    public static McpEvent fromResourcesChange(List<McpSchema.Resource> resources) {
        return McpEvent.builder()
                .type(EventType.RESOURCES_CHANGE)
                .timestamp(Instant.now())
                .payload(resources)
                .build();
    }

    public static McpEvent fromResourcesUpdate(List<McpSchema.ResourceContents> resources) {
        return McpEvent.builder()
                .type(EventType.RESOURCES_UPDATE)
                .timestamp(Instant.now())
                .payload(resources)
                .build();
    }

    public static McpEvent fromPromptsChange(List<McpSchema.Prompt> prompts) {
        return McpEvent.builder()
                .type(EventType.PROMPTS_CHANGE)
                .timestamp(Instant.now())
                .payload(prompts)
                .build();
    }

    public static McpEvent fromProgressNotification(McpSchema.ProgressNotification progressNotification) {
        return McpEvent.builder()
                .type(EventType.PROGRESS)
                .timestamp(Instant.now())
                .payload(progressNotification)
                .build();
    }

    public static McpEvent fromLoggingNotification(McpSchema.LoggingMessageNotification loggingMessageNotification) {
        return McpEvent.builder()
                .type(EventType.LOGGING)
                .timestamp(Instant.now())
                .payload(loggingMessageNotification)
                .build();
    }
}

