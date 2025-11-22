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

    // ==================== TOOLS ====================

    @Then(THAT + GUARD + "the (tools|prompts|resources) (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_tools_contains(Guard guard, String requestType, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            mcpListRequest(requestType, comparison, content);
        });
    }

    private void mcpListRequest(String requestType, Comparison comparison, Object content) {
        List<Map> expected = readAsAListOf(objects.resolve(content), Map.class);
        McpResponse response = switch (requestType) {
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

        // Store response
        objects.add(MCP_RESPONSE_KEY, response);

        // Compare (use first content item)
        comparison.compare(response.content.get(0).payload, expected);
    }

    @When(THAT + GUARD + "we calls the tool " + QUOTED_CONTENT + ":$")
    public void call_a_tool(Guard guard, String toolName, String content) {
        guard.in(objects, () -> {
            McpResponse response;
            try {
                McpSchema.CallToolRequest callToolRequest = new McpSchema.CallToolRequest(toolName, Mapper.read(objects.resolve(content)));
                McpSchema.CallToolResult result = mcpAsyncClient.callTool(callToolRequest).block();
                response = McpResponse.fromCallToolResult(result);
            } catch (Exception e) {
                response = McpResponse.builder().isError(true).error(e.getMessage()).build();
            }
            objects.add(MCP_RESPONSE_KEY, response);
        });
    }

    // ==================== RESOURCES ====================

    @When(THAT + GUARD + "we read the resource " + QUOTED_CONTENT + "$")
    public void read_resource(Guard guard, String resourceUri) {
        guard.in(objects, () -> {
            McpResponse response;
            try {
                McpSchema.ReadResourceRequest readRequest = new McpSchema.ReadResourceRequest(resourceUri);
                McpSchema.ReadResourceResult result = mcpAsyncClient.readResource(readRequest).block();
                response = McpResponse.fromReadResourceResult(result);
            } catch (Exception e) {
                response = McpResponse.builder().isError(true).error(e.getMessage()).content(List.of(McpResponse.ResponseContent.builder().type("text").payload(null).build())).build();
            }
            objects.add(MCP_RESPONSE_KEY, response);
        });
    }

    @Then(THAT + GUARD + "the resource " + QUOTED_CONTENT + " contains" + COMPARING_WITH + ":$")
    public void the_resource_contains(Guard guard, String resourceUri, Comparison comparison, String content) {
        guard.in(objects, () -> {
            // First read the resource
            read_resource(guard, resourceUri);

            // Then compare
            McpResponse response = objects.get(MCP_RESPONSE_KEY);
            String payload = objects.resolve(content);

            comparison.compare(response.content.get(0).payload, payload);
        });
    }

    // ==================== PROMPTS ====================

    @When(THAT + GUARD + "we get the prompt " + QUOTED_CONTENT + "$")
    public void get_prompt(Guard guard, String promptName) {
        guard.in(objects, () -> {
            McpResponse response;
            try {
                McpSchema.GetPromptRequest getPromptRequest = new McpSchema.GetPromptRequest(promptName, null);
                McpSchema.GetPromptResult result = mcpAsyncClient.getPrompt(getPromptRequest).block();
                response = McpResponse.fromGetPromptResult(result);
            } catch (Exception e) {
                response = McpResponse.builder().isError(true).error(e.getMessage()).content(List.of(McpResponse.ResponseContent.builder().type("text").payload(null).build())).build();
            }
            objects.add(MCP_RESPONSE_KEY, response);
        });
    }

    @When(THAT + GUARD + "we get the prompt " + QUOTED_CONTENT + " with arguments:$")
    public void get_prompt_with_arguments(Guard guard, String promptName, String content) {
        guard.in(objects, () -> {
            McpResponse response;
            try {
                McpSchema.GetPromptRequest getPromptRequest = new McpSchema.GetPromptRequest(promptName, Mapper.read(objects.resolve(content)));
                McpSchema.GetPromptResult result = mcpAsyncClient.getPrompt(getPromptRequest).block();
                response = McpResponse.fromGetPromptResult(result);
            } catch (Exception e) {
                response = McpResponse.builder().isError(true).error(e.getMessage()).content(List.of(McpResponse.ResponseContent.builder().type("text").payload(null).build())).build();
            }
            objects.add(MCP_RESPONSE_KEY, response);
        });
    }

    @Then(THAT + GUARD + "the prompt " + QUOTED_CONTENT + " contains" + COMPARING_WITH + ":$")
    public void the_prompt_contains(Guard guard, String promptName, Comparison comparison, String content) {
        guard.in(objects, () -> {
            // First get the prompt
            get_prompt(guard, promptName);

            // Then compare
            McpResponse response = objects.get(MCP_RESPONSE_KEY);
            String payload = objects.resolve(content);

            comparison.compare(response.content.get(0).payload, payload);
        });
    }

    // ==================== GENERIC RESPONSE STEPS ====================

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

    @Then(THAT + GUARD + "the response error is " + QUOTED_CONTENT + "$")
    public void the_response_error_is(Guard guard, String expectedError) {
        guard.in(objects, () -> {
            McpResponse response = objects.get(MCP_RESPONSE_KEY);
            if (!response.isError) {
                throw new AssertionError("Expected an error but got a successful response");
            }
            String actualError = response.error;
            String expected = objects.resolve(expectedError);
            if (!actualError.contains(expected)) {
                throw new AssertionError("Expected error to contain '" + expected + "' but got: " + actualError);
            }
        });
    }
}
