package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonValue;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Response - represents a response from an MCP server
 */
@Builder(toBuilder = true)
public class McpResponse {

    @Builder.Default
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<ResponseContent> content = List.of();

    @Getter
    private String error;
    @Getter
    private Boolean isError;

    @Builder.Default
    public Object structuredContent = null;

    public static McpResponse fromCallToolResult(McpSchema.CallToolResult result) {

        List<ResponseContent> contentList = result.content().stream()
                .map(c -> {
                    ResponseContent.ResponseContentBuilder builder = ResponseContent.builder();
                    builder.type(ContentType.fromString(c.type()));
                    if (c instanceof McpSchema.Annotated annotated) {
                        builder.annotations(annotated.annotations() != null ? Mapper.read(Mapper.toJson(annotated.annotations()), Map.class) : Map.of());
                    }
                    return builder.payload(getPayloadFromContent(c)).build();
                })
                .toList();

        return McpResponse.builder()
                .content(contentList.isEmpty() ? List.of(new ResponseContent()) : contentList)
                .isError(result.isError())
                .structuredContent(result.structuredContent())
                .build();
    }

    private static Object getPayloadFromContent(McpSchema.Content content) {
        if (content instanceof McpSchema.TextContent textContent) {
            return textContent.text();
        } else if (content instanceof McpSchema.ImageContent imageContent) {
            return imageContent.data();
        } else if (content instanceof McpSchema.EmbeddedResource embeddedResource) {
            return embeddedResource.resource();
        } else {
            return content;
        }
    }

    public static McpResponse fromReadResourceResult(McpSchema.ReadResourceResult result) {
        List<ResponseContent> contentList = result.contents().stream()
                .map(content -> {
                    if (content instanceof McpSchema.TextResourceContents textRes) {
                        return ResponseContent.builder()
                                .payload(textRes.text())
                                .type(ContentType.TEXT_RESOURCE)
                                .build();
                    } else if (content instanceof McpSchema.BlobResourceContents blobRes) {
                        return ResponseContent.builder()
                                .payload(blobRes.blob())
                                .type(ContentType.BLOB_RESOURCE)
                                .build();
                    } else {
                        return ResponseContent.builder()
                                .payload(content)
                                .type(ContentType.UNKNOWN)
                                .build();
                    }
                })
                .toList();

        return McpResponse.builder()
                .content(contentList)
                .build();
    }

    public static McpResponse fromGetPromptResult(McpSchema.GetPromptResult result) {
        List<PromptMsg> messages = result.messages().stream()
                .map(msg -> new PromptMsg(
                        msg.role().toString(),
                        getPayloadFromContent(msg.content())
                ))
                .toList();

        return McpResponse.builder()
                .content(List.of(ResponseContent.builder()
                        .payload(messages.size() == 1 ? messages.get(0) : messages)
                        .type(ContentType.LIST)
                        .build()))
                .build();
    }

    public static McpResponse fromListToolsResult(McpSchema.ListToolsResult result) {
        if (result == null || result.tools() == null) {
            return McpResponse.builder()
                    .error("No tools returned")
                    .isError(true)
                    .build();
        }

        List<ToolDescription> tools = result.tools().stream()
                .map(tool -> new ToolDescription(
                        tool.name(),
                        tool.description(),
                        tool.inputSchema(),
                        tool.outputSchema()
                ))
                .toList();

        return McpResponse.builder()
                .content(List.of(ResponseContent.builder()
                        .payload(tools)
                        .type(ContentType.LIST)
                        .build()))
                .build();
    }

    public static McpResponse fromListResourcesResult(McpSchema.ListResourcesResult result) {
        if (result == null || result.resources() == null) {
            return McpResponse.builder()
                    .error("No resources returned")
                    .isError(true)
                    .build();
        }

        List<ResourceDescription> resources = result.resources().stream()
                .map(resource -> new ResourceDescription(
                        resource.uri(),
                        resource.name(),
                        resource.title(),
                        resource.description(),
                        resource.mimeType()
                ))
                .toList();

        return McpResponse.builder()
                .content(List.of(ResponseContent.builder()
                        .payload(resources)
                        .type(ContentType.LIST)
                        .build()))
                .build();
    }

    public static McpResponse fromListPromptsResult(McpSchema.ListPromptsResult result) {
        if (result == null || result.prompts() == null) {
            return McpResponse.builder()
                    .error("No prompts returned")
                    .isError(true)
                    .build();
        }

        List<PromptDescription> prompts = result.prompts().stream()
                .map(prompt -> new PromptDescription(
                        prompt.name(),
                        prompt.description(),
                        prompt.title(),
                        prompt.arguments()
                ))
                .toList();

        return McpResponse.builder()
                .content(List.of(ResponseContent.builder()
                        .payload(prompts)
                        .type(ContentType.LIST)
                        .build()))
                .build();
    }

    /**
     * PromptMsg record for prompt messages
     */
    public record PromptMsg(String role, Object content) {
    }

    /**
     * ToolDescription record for tool information
     */
    public record ToolDescription(String name, String description, Object inputSchema, Object outputSchema) {
    }

    /**
     * ResourceDescription record for resource information
     */
    public record ResourceDescription(String uri, String name, String title, String description, String mimeType) {
    }

    /**
     * PromptDescription record for prompt information
     */
    public record PromptDescription(String name, String description, String title,
                                    List<McpSchema.PromptArgument> arguments) {
    }

    /**
     * Content type enum for ResponseContent
     */
    public enum ContentType {
        TEXT("text"),
        IMAGE("image"),
        RESOURCE("resource"),
        TEXT_RESOURCE("TextResource"),
        BLOB_RESOURCE("BlobResource"),
        LIST("List"),
        UNKNOWN("Unknown");

        private final String value;

        ContentType(String value) {
            this.value = value;
        }

        public static ContentType fromString(String value) {
            if (value == null) {
                return UNKNOWN;
            }
            for (ContentType type : ContentType.values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return UNKNOWN;
        }

        @JsonValue
        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * ResponseContent class for response payload
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class ResponseContent {

        @Builder.Default
        public ContentType type = ContentType.TEXT;

        @Builder.Default
        public Map<String, Object> annotations = new LinkedHashMap<>();

        @Getter
        private Object payload;
    }
}
