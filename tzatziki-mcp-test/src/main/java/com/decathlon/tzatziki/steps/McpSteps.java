package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.config.McpClientConfiguration;
import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Mapper.readAsAListOf;
import static com.decathlon.tzatziki.utils.Patterns.*;

@Slf4j
public class McpSteps {

    private static final String MCP_RESPONSE_KEY = "_mcpResponse";

    @Getter
    private static final List<McpEvent> mcpEvents = Collections.synchronizedList(new ArrayList<>());

    private static McpClientConfiguration mcpClientConfiguration;

    private final McpAsyncClient mcpAsyncClient;
    private final ObjectSteps objects;

    public McpSteps(ObjectSteps objects) {
        this.objects = objects;
        if (mcpClientConfiguration == null) {
            mcpClientConfiguration = new McpClientConfiguration();
        }
        mcpEvents.clear();
        this.mcpAsyncClient = mcpClientConfiguration.getMcpAsyncClient();
    }

    @AfterAll
    public static void afterAll() {
        if (mcpClientConfiguration != null) {
            mcpClientConfiguration.close();
            mcpClientConfiguration = null;
        }
    }

    private McpResponse createErrorResponse(Exception e) {
        return McpResponse.builder()
                .isError(true)
                .error(e.getMessage())
                .content(List.of(McpResponse.ResponseContent.builder()
                        .type(McpResponse.ContentType.TEXT)
                        .payload(null)
                        .build()))
                .build();
    }

    @Then(THAT + GUARD + "the (tools|prompts|resources) (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_tools_contains(Guard guard, String requestType, Comparison comparison, Object content) {
        guard.in(objects, () -> mcpListRequest(requestType, comparison, content));
    }

    private void mcpListRequest(String requestType, Comparison comparison, Object content) {
        List<Map> expected = readAsAListOf(objects.resolve(content), Map.class);
        McpResponse response;
        try {
            response = switch (requestType) {
            case "tools" -> {
                McpSchema.ListToolsResult result = mcpAsyncClient.listTools().block();
                yield McpResponse.fromListToolsResult(result);
            }
            case "resources" -> {
                McpSchema.ListResourcesResult result = mcpAsyncClient.listResources().block();
                yield McpResponse.fromListResourcesResult(result);
            }
            case "prompts" -> {
                McpSchema.ListPromptsResult result = mcpAsyncClient.listPrompts().block();
                yield McpResponse.fromListPromptsResult(result);
            }

                default -> throw new IllegalArgumentException("Unknown request type: " + requestType);
            };
        } catch (Exception e) {
            response = createErrorResponse(e);
        }

        objects.add(MCP_RESPONSE_KEY, response);

        comparison.compare(response.content.get(0).getPayload(), expected);
    }

    @When(THAT + GUARD + "we call the (tool|prompt|resource) " + QUOTED_CONTENT + ":$")
    public void call_a_tool(Guard guard, String resourceType, String toolName, String content) {
        guard.in(objects, () -> mcpCallRequest(resourceType, toolName, content));
    }

    @When(THAT + GUARD + "we call the (tool|prompt|resource) " + QUOTED_CONTENT + "$")
    public void call_a_tool(Guard guard, String resourceType, String toolName) {
        guard.in(objects, () -> mcpCallRequest(resourceType, toolName, null));
    }

    private void mcpCallRequest(String requestType, String resourceName, Object content) {
        Map<String, Object> contentMap = content != null ? Mapper.read(objects.resolve(content)) : Map.of();
        McpResponse response;
        try {
            response = switch (requestType) {
                case "tool" -> {
                    McpSchema.CallToolRequest.Builder callToolRequest = McpSchema.CallToolRequest.builder().name(resourceName).arguments(contentMap);
                    if (contentMap.containsKey("request-meta")) {
                        Object meta = contentMap.remove("request-meta");
                        if (meta instanceof Map metaMap) {
                            callToolRequest.meta(metaMap);
                        }
                    }
                    McpSchema.CallToolResult result = mcpAsyncClient.callTool(callToolRequest.build()).block();
                    yield McpResponse.fromCallToolResult(result);
                }
                case "resource" -> {
                    McpSchema.ReadResourceRequest readRequest = new McpSchema.ReadResourceRequest(resourceName);
                    McpSchema.ReadResourceResult result = mcpAsyncClient.readResource(readRequest).block();
                    yield McpResponse.fromReadResourceResult(result);
                }
                case "prompt" -> {
                    McpSchema.GetPromptRequest getPromptRequest = new McpSchema.GetPromptRequest(resourceName,
                            content != null ? Mapper.read(objects.resolve(content)) : null);
                    McpSchema.GetPromptResult result = mcpAsyncClient.getPrompt(getPromptRequest).block();
                    yield McpResponse.fromGetPromptResult(result);
                }

                default -> throw new IllegalArgumentException("Unknown request type: " + requestType);
            };
        } catch (Exception e) {
            response = createErrorResponse(e);
        }

        objects.add(MCP_RESPONSE_KEY, response);
    }

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)? from mcp" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
    public void we_receive(Guard guard, Comparison comparison, Type type, String content) {
        guard.in(objects, () -> {
            McpResponse response = objects.get(MCP_RESPONSE_KEY);
            String payload = objects.resolve(content);

            if (McpResponse.class.equals(type)) {
                Map<String, Object> expected = Mapper.read(payload);
                comparison.compare(response, expected);
            } else {
                comparison.compare(response.content.stream().map(McpResponse.ResponseContent::getPayload).toList(), List.of(payload));
            }
        });
    }

    @Then(THAT + GUARD + "the response contains an error$")
    public void the_response_contains_an_error(Guard guard) {
        guard.in(objects, () -> {
            McpResponse response = objects.get(MCP_RESPONSE_KEY);
            if (response == null || !response.getIsError()) {
                throw new AssertionError("Expected an error but got a successful response");
            }
        });
    }

    @Then(THAT + GUARD + "the mcp events (?:list )?contains" + COMPARING_WITH + ":$")
    public void the_mcp_events_contains(Guard guard, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            List<Map> expected = readAsAListOf(objects.resolve(content), Map.class);
            comparison.compare(mcpEvents, expected);
        });
    }

    @When(THAT + GUARD + "we subscribe to the resource " + QUOTED_CONTENT + "$")
    public void subscribe_to_resource(Guard guard, String resourceUri) {
        guard.in(objects, () -> {
            try {
                McpSchema.SubscribeRequest subscribeRequest = new McpSchema.SubscribeRequest(resourceUri);
                mcpAsyncClient.subscribeResource(subscribeRequest).block();
            } catch (Exception e) {
                throw new AssertionError("Failed to subscribe to resource: " + resourceUri, e);
            }
        });
    }

    @When(THAT + GUARD + "we unsubscribe from the resource " + QUOTED_CONTENT + "$")
    public void unsubscribe_from_resource(Guard guard, String resourceUri) {
        guard.in(objects, () -> {
            try {
                McpSchema.UnsubscribeRequest unsubscribeRequest = new McpSchema.UnsubscribeRequest(resourceUri);
                mcpAsyncClient.unsubscribeResource(unsubscribeRequest).block();
            } catch (Exception e) {
                throw new AssertionError("Failed to unsubscribe from resource: " + resourceUri, e);
            }
        });
    }
}
