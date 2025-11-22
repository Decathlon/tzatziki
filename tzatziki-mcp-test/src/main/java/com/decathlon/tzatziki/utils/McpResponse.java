package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Response - represents a response from an MCP server
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class McpResponse {

    private static final String DESCRIPTION = "description";
    private static final String CONTENT_KEY = "content";

    @Builder.Default
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<ResponseContent> content = List.of();

    public String error;
    public Boolean isError;

    @Builder.Default
    public Map<String, Object> metadata = new LinkedHashMap<>();

    @Builder.Default
    public Object structuredContent = null;

    public static McpResponse fromCallToolResult(McpSchema.CallToolResult result) {
        List<ResponseContent> contentList = result.content().stream()
                .map(c -> {
                    ResponseContent.ResponseContentBuilder builder = ResponseContent.builder();
                    builder.type(c.type());
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
                                .type("TextResource")
                                .build();
                    } else if (content instanceof McpSchema.BlobResourceContents blobRes) {
                        return ResponseContent.builder()
                                .payload(blobRes.blob()) // Base64 encoded
                                .type("BlobResource")
                                .build();
                    } else {
                        return ResponseContent.builder()
                                .payload(content)
                                .type("Unknown")
                                .build();
                    }
                })
                .toList();

        return McpResponse.builder()
                .content(contentList)
                .build();
    }

    public static McpResponse fromGetPromptResult(McpSchema.GetPromptResult result) {
        if (result == null || result.messages() == null || result.messages().isEmpty()) {
            return McpResponse.builder()
                    .error("No prompt messages returned")
                    .isError(true)
                    .build();
        }

        // Convert messages to a structured format
        List<Map<String, Object>> messages = result.messages().stream()
                .map(msg -> {
                    Map<String, Object> msgMap = new LinkedHashMap<>();
                    msgMap.put("role", msg.role().toString());
                    msgMap.put(CONTENT_KEY, getPayloadFromContent(msg.content()));
                    return msgMap;
                })
                .toList();

        return McpResponse.builder()
                .content(List.of(ResponseContent.builder()
                        .payload(messages.size() == 1 ? messages.get(0) : messages)
                        .type("List")
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

        List<Map<String, Object>> tools = result.tools().stream()
                .map(tool -> {
                    Map<String, Object> toolMap = new LinkedHashMap<>();
                    toolMap.put("name", tool.name());
                    toolMap.put(DESCRIPTION, tool.description());
                    toolMap.put("inputSchema", tool.inputSchema());
                    toolMap.put("outputSchema", tool.outputSchema());
                    return toolMap;
                })
                .toList();

        return McpResponse.builder()
                .content(List.of(ResponseContent.builder()
                        .payload(tools)
                        .type("List")
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

        List<Map<String, Object>> resources = result.resources().stream()
                .map(resource -> {
                    Map<String, Object> resMap = new LinkedHashMap<>();
                    resMap.put("uri", resource.uri());
                    resMap.put("name", resource.name());
                    resMap.put("title", resource.title());
                    resMap.put(DESCRIPTION, resource.description());
                    resMap.put("mimeType", resource.mimeType());
                    return resMap;
                })
                .toList();

        return McpResponse.builder()
                .content(List.of(ResponseContent.builder()
                        .payload(resources)
                        .type("List")
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

        List<Map<String, Object>> prompts = result.prompts().stream()
                .map(prompt -> {
                    Map<String, Object> promptMap = new LinkedHashMap<>();
                    promptMap.put("name", prompt.name());
                    promptMap.put(DESCRIPTION, prompt.description());
                    promptMap.put("title", prompt.title());
                    promptMap.put("arguments", prompt.arguments());
                    return promptMap;
                })
                .toList();

        return McpResponse.builder()
                .content(List.of(ResponseContent.builder()
                        .payload(prompts)
                        .type("List")
                        .build()))
                .build();
    }

    /**
     * ResponseContent class for response payload
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder(toBuilder = true)
    public static class ResponseContent {

        @Builder.Default
        public String type = String.class.getSimpleName();

        @Builder.Default
        public Map<String, Object> annotations = new LinkedHashMap<>();

        public Object payload;

        public String toString() {
            if (payload == null) {
                return null;
            }

            if (payload instanceof String payloadString) {
                try {
                    Class<?> clazz = getTypeClass();
                    return clazz.equals(String.class) ? payloadString :
                            Mapper.toJson(Mapper.read(payloadString, clazz));
                } catch (Exception e) {
                    return payloadString;
                }
            } else {
                String body = Mapper.toJson(payload);
                Class<?> clazz = getTypeClass();
                if (!clazz.equals(String.class)) {
                    body = Mapper.toJson(Mapper.read(body, clazz));
                }
                return body;
            }
        }


        private Class<?> getTypeClass() {
            try {
                return switch (type) {
                    case "Map", "java.util.Map" -> Map.class;
                    case "List", "java.util.List" -> List.class;
                    default -> String.class;
                };
            } catch (Exception e) {
                return String.class;
            }
        }
    }
}
