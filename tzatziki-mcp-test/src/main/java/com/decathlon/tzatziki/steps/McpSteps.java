package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.config.McpClientConfiguration;
import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Mapper;
import com.decathlon.tzatziki.utils.McpResponse;
import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Mapper.readAsAListOf;
import static com.decathlon.tzatziki.utils.Patterns.*;

@Slf4j
public class McpSteps {

    private static final String MCP_RESPONSE_KEY = "_mcpResponse";

    private static McpClientConfiguration mcpClientConfiguration;

    private final McpAsyncClient mcpAsyncClient;
    private final ObjectSteps objects;

    public McpSteps(ObjectSteps objects) {
        this.objects = objects;
        if (mcpClientConfiguration == null) {
            mcpClientConfiguration = new McpClientConfiguration();
        }
        this.mcpAsyncClient = mcpClientConfiguration.getMcpAsyncClient();
    }

    @AfterAll
    public static void afterAll() {
        if (mcpClientConfiguration != null) {
            mcpClientConfiguration.close();
            mcpClientConfiguration = null;
        }
    }

    @Then(THAT + GUARD + "the (tools|prompts|resources) (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_tools_contains(Guard guard, String requestType, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            mcpListRequest(requestType, comparison, content);
        });
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
            response = McpResponse.builder().isError(true).error(e.getMessage()).content(List.of(McpResponse.ResponseContent.builder().type(McpResponse.ContentType.TEXT).payload(null).build())).build();
        }

        // Store response
        objects.add(MCP_RESPONSE_KEY, response);

        // Compare (use first content item)
        comparison.compare(response.content.get(0).payload, expected);
    }

    @When(THAT + GUARD + "we call the (tool|prompt|resource) " + QUOTED_CONTENT + ":$")
    public void call_a_tool(Guard guard, String resourceType, String toolName, String content) {
        guard.in(objects, () -> {
            mcpCallRequest(resourceType, toolName, content);
        });
    }

    @When(THAT + GUARD + "we call the (tool|prompt|resource) " + QUOTED_CONTENT + "$")
    public void call_a_tool(Guard guard, String resourceType, String toolName) {
        guard.in(objects, () -> {
            mcpCallRequest(resourceType, toolName, null);
        });
    }

    private void mcpCallRequest(String requestType, String resourceName, Object content) {
        McpResponse response;
        try {
            response = switch (requestType) {
                case "tool" -> {
                    McpSchema.CallToolRequest callToolRequest = new McpSchema.CallToolRequest(resourceName,
                            content != null ? Mapper.read(objects.resolve(content)) : null);
                    McpSchema.CallToolResult result = mcpAsyncClient.callTool(callToolRequest).block();
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
            response = McpResponse.builder().isError(true).error(e.getMessage()).content(List.of(McpResponse.ResponseContent.builder().type(McpResponse.ContentType.TEXT).payload(null).build())).build();
        }


        // Store response
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
                comparison.compare(response.content.stream().map(c -> c.payload).toList(), List.of(payload));
            }
        });
    }

    @Then(THAT + GUARD + "the response contains an error$")
    public void the_response_contains_an_error(Guard guard) {
        guard.in(objects, () -> {
            McpResponse response = objects.get(MCP_RESPONSE_KEY);
            if (!response.isError) {
                throw new AssertionError("Expected an error but got a successful response");
            }
        });
    }
}
