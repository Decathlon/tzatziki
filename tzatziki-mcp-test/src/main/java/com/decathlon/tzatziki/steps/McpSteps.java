package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.config.McpClientConfiguration;
import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Mapper.read;
import static com.decathlon.tzatziki.utils.Patterns.*;

@Slf4j
public class McpSteps {
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
        mcpClientConfiguration.close();
    }

    @Then(THAT + GUARD + "the tools (?:still )?contains" + COMPARING_WITH + ":$")
    public void the_tools_contains(Guard guard, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            List<Map> expectedTools = Mapper.readAsAListOf(objects.resolve(content), Map.class);
            List<McpSchema.Tool> actualTools = mcpAsyncClient.listTools().block().tools();
            comparison.compare(actualTools, expectedTools);
        });
    }

    @When(THAT + GUARD + "we calls the tool " + QUOTED_CONTENT + ":$")
    public void call_a_tool(Guard guard, String toolName, String content) {
        guard.in(objects, () -> {
            Map toolCall = read(objects.resolve(content), Map.class);
            McpSchema.CallToolRequest callToolRequest = new McpSchema.CallToolRequest(toolName, toolCall);
            McpSchema.CallToolResult result = mcpAsyncClient.callTool(callToolRequest).block();
            String resultText = result.content().stream().filter(c -> c instanceof McpSchema.TextContent).map(McpSchema.TextContent.class::cast)
                    .map(textContent -> textContent.text()).findFirst().orElse(null);
            objects.add("_response", Mapper.toJson(resultText));
        });
    }

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)? from tool" + COMPARING_WITH + ":$")
    public void we_receive(Guard guard, Comparison comparison, String content) {
        guard.in(objects, () -> {
            Object response = objects.get("_response");
            String payload = objects.resolve(content);
            comparison.compare(response, payload);
        });
    }
}


