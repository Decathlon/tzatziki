# User Provided Header
Tzatziki MCP (Model Context Protocol) module reference.
- McpSteps.java defines @Given/@When/@Then patterns for MCP server testing, tool invocation, and AI integration assertions.
- .feature files demonstrate valid MCP step usage.


# Directory Structure
```
tzatziki-test-mcp/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                McpSteps.java
    test/
      resources/
        features/
          mcp-everything-server.feature
          mcp-weather-server.feature
```

# Files

## File: tzatziki-test-mcp/src/main/java/com/decathlon/tzatziki/steps/McpSteps.java
```java
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
@SuppressWarnings("java:S100") // Allow method names with underscores for BDD steps
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

    @Then(THAT + GUARD + "the MCP (tools|prompts|resources) (?:still )?contains" + COMPARING_WITH + ":$")
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

    @When(THAT + GUARD + "we call the MCP (tool|prompt|resource) " + QUOTED_CONTENT + ":$")
    public void call_a_tool(Guard guard, String resourceType, String toolName, String content) {
        guard.in(objects, () -> mcpCallRequest(resourceType, toolName, content));
    }

    @When(THAT + GUARD + "we call the MCP (tool|prompt|resource) " + QUOTED_CONTENT + "$")
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

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)? from MCP" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
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

    @Then(THAT + GUARD + "the MCP response contains an error$")
    public void the_response_contains_an_error(Guard guard) {
        guard.in(objects, () -> {
            McpResponse response = objects.get(MCP_RESPONSE_KEY);
            if (response == null || !response.getIsError()) {
                throw new AssertionError("Expected an error but got a successful response");
            }
        });
    }

    @Then(THAT + GUARD + "the MCP events (?:list )?contains" + COMPARING_WITH + ":$")
    public void the_mcp_events_contains(Guard guard, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            List<Map> expected = readAsAListOf(objects.resolve(content), Map.class);
            comparison.compare(mcpEvents, expected);
        });
    }

    @When(THAT + GUARD + "we subscribe to the MCP resource " + QUOTED_CONTENT + "$")
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

    @When(THAT + GUARD + "we unsubscribe from the MCP resource " + QUOTED_CONTENT + "$")
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
```

## File: tzatziki-test-mcp/src/test/resources/features/mcp-everything-server.feature
```
Feature: MCP Everything Server Testing
  Test all features of the MCP Everything Server via sse transport
  The everything server demonstrates all MCP protocol capabilities

    # ==================== TOOLS TESTING ====================

  Scenario: List all available tools from everything server
    Then the MCP tools contains:
    """
    - name: "echo"
      description: "Echoes back the input"
      inputSchema:
        type: "object"
        properties:
          message:
            type: "string"
            description: "Message to echo"
        required:
        - "message"
        additionalProperties: false
    - name: "add"
      description: "Adds two numbers"
      inputSchema:
        type: "object"
        properties:
          a:
            type: "number"
            description: "First number"
          b:
            type: "number"
            description: "Second number"
        required:
        - "a"
        - "b"
        additionalProperties: false
    - name: "longRunningOperation"
      description: "Demonstrates a long running operation with progress updates"
      inputSchema:
        type: "object"
        properties:
          duration:
            type: "number"
            default: 10
            description: "Duration of the operation in seconds"
          steps:
            type: "number"
            default: 5
            description: "Number of steps in the operation"
        additionalProperties: false
    - name: "printEnv"
      description: "Prints all environment variables, helpful for debugging MCP server configuration"
      inputSchema:
        type: "object"
        properties: {}
        additionalProperties: false
    - name: "sampleLLM"
      description: "Samples from an LLM using MCP's sampling feature"
      inputSchema:
        type: "object"
        properties:
          prompt:
            type: "string"
            description: "The prompt to send to the LLM"
          maxTokens:
            type: "number"
            default: 100
            description: "Maximum number of tokens to generate"
        required:
        - "prompt"
        additionalProperties: false
    - name: "getTinyImage"
      description: "Returns the MCP_TINY_IMAGE"
      inputSchema:
        type: "object"
        properties: {}
        additionalProperties: false
    - name: "annotatedMessage"
      description: "Demonstrates how annotations can be used to provide metadata about content"
      inputSchema:
        type: "object"
        properties:
          messageType:
            type: "string"
            enum:
            - "error"
            - "success"
            - "debug"
            description: "Type of message to demonstrate different annotation patterns"
          includeImage:
            type: "boolean"
            default: false
            description: "Whether to include an example image"
        required:
        - "messageType"
        additionalProperties: false
    - name: "getResourceReference"
      description: "Returns a resource reference that can be used by MCP clients"
      inputSchema:
        type: "object"
        properties:
          resourceId:
            type: "number"
            minimum: 1
            maximum: 100
            description: "ID of the resource to reference (1-100)"
        required:
        - "resourceId"
        additionalProperties: false
    - name: "getResourceLinks"
      description: "Returns multiple resource links that reference different types of resources"
      inputSchema:
        type: "object"
        properties:
          count:
            type: "number"
            minimum: 1
            maximum: 10
            default: 3
            description: "Number of resource links to return (1-10)"
        additionalProperties: false
    - name: "structuredContent"
      description: "Returns structured content along with an output schema for client data validation"
      inputSchema:
        type: "object"
        properties:
          location:
            type: "string"
            minLength: 1
            description: "City name or zip code"
        required:
        - "location"
        additionalProperties: false
      outputSchema:
        type: "object"
        properties:
          temperature:
            type: "number"
            description: "Temperature in celsius"
          conditions:
            type: "string"
            description: "Weather conditions description"
          humidity:
            type: "number"
            description: "Humidity percentage"
        required:
        - "temperature"
        - "conditions"
        - "humidity"
        additionalProperties: false
        $schema: "http://json-schema.org/draft-07/schema#"
    - name: "listRoots"
      description: "Lists the current MCP roots provided by the client. Demonstrates the roots protocol capability even though this server doesn't access files."
      inputSchema:
        type: "object"
        properties: {}
        additionalProperties: false
    - name: "startElicitation"
      description: "Demonstrates the Elicitation feature by asking the user to provide information about their favorite color, number, and pets."
      inputSchema:
        type: "object"
        properties: {}
        additionalProperties: false
    """

    And the MCP events list contains:
    """
    - type: "LOGGING"
      timestamp: ?after {{{[@20 mins ago]}}}
      payload:
        level: "info"
        logger: "everything-server"
        data: "Initial roots received: 1 root(s) from client"
    """

  Scenario: Call echo tool with simple message
    When we call the MCP tool "echo":
    """
    message: Hello from Tzatziki!
    """
    Then we receive from MCP:
    """
    Echo: Hello from Tzatziki!
    """

  Scenario: Call add tool with numbers
    When we call the MCP tool "add":
    """
    a: 5
    b: 3
    """
    Then we receive from MCP:
    """
    The sum of 5 and 3 is 8.
    """

  Scenario: Call longRunningOperation tool
    When we call the MCP tool "longRunningOperation":
    """
    request-meta:
      progressToken: test-token
    duration: 1
    steps: 2
    """
    Then we receive from MCP:
    """
    Long running operation completed. Duration: 1 seconds, Steps: 2.
    """

    And the MCP events list contains:
    """
    - type: "PROGRESS"
      timestamp: ?after {{{[@20 mins ago]}}}
      payload:
        progressToken: "test-token"
        progress: 1.0
        total: 2.0
    - type: "PROGRESS"
      timestamp: ?after {{{[@20 mins ago]}}}
      payload:
        progressToken: "test-token"
        progress: 2.0
        total: 2.0
    """

  Scenario: Call sampleLLM tool
    When we call the MCP tool "sampleLLM":
    """
    prompt: What is 2+2?
    maxTokens: 50
    """
    Then we receive from MCP:
    """
    LLM sampling result: Resource sampleLLM context: What is 2+2?
    """

  Scenario: Call getTinyImage tool and get base64 response
    When we call the MCP tool "getTinyImage":
    """
    prompt: What is 2+2?
    maxTokens: 50
    """
    Then we receive from MCP a McpResponse:
    """
      content:
        - type: text
          payload: 'This is a tiny image:'
        - type: image
          annotations: {}
          payload: ?notNull
        - type: text
          annotations: {}
          payload: The image above is the MCP tiny image.
    """

  Scenario: Call annotatedMessage tool and get response with annotations
    When we call the MCP tool "annotatedMessage":
    """
    messageType: error
    includeImage: false
    """
    Then we receive from MCP a McpResponse:
    """
      content:
      - annotations:
          audience:
          - "user"
          - "assistant"
          priority: 1.0
        payload: "Error: Operation failed"
    """

  Scenario: Call annotatedMessage tool and get response error
    When we call the MCP tool "annotatedMessage":
    """
    messageType: blabla
    includeImage: false
    """
    Then we receive from MCP a McpResponse:
    """
      isError: true
      error:
        - received: "blabla"
          code: "invalid_enum_value"
    """

  Scenario: Call getResourceReference tool and get a resource reference
    When we call the MCP tool "getResourceReference":
    """
    resourceId: 14
    """
    Then we receive from MCP exactly a McpResponse:
    """
    content:
      - type: "text"
        annotations: {}
        payload: "Returning resource reference for Resource 14:"
      - type: "resource"
        annotations: {}
        payload:
          uri: "test://static/resource/14"
          mimeType: "application/octet-stream"
          blob: "UmVzb3VyY2UgMTQ6IFRoaXMgaXMgYSBiYXNlNjQgYmxvYg=="
      - type: "text"
        annotations: {}
        payload: "You can access this resource using the URI: test://static/resource/14"
    error: null
    isError: null
    structuredContent: null
    """

  Scenario: Can start elicitation tool and mock elicitation flow
    When we call the MCP tool "startElicitation":
    """
    color: red
    number: 13
    pets: cat
    """
    Then we receive from MCP a McpResponse:
    """
    content:
      - type: "text"
        payload: "✅ User provided their favorite things!"
      - type: "text"
        payload: "Their favorites are:\n- Color: not specified\n- Number: not specified\n- Pets: not specified"
      - type: "text"
        payload: "\nRaw result: {\n  \"action\": \"accept\",\n  \"content\": {\n    \"message\": \"elicitation response\"\n  }\n}"
    """

  Scenario: Call structuredContent tool and get response with structuredContent
    When we call the MCP tool "structuredContent":
    """
    location: "Lille"
    """
    Then we receive from MCP a McpResponse:
    """
      structuredContent:
        temperature: 22.5
        humidity: 65
    """

  Scenario: Call listRoots tool to list the current MCP roots
    When we call the MCP tool "listRoots":
    """
    location: "Lille"
    """
    Then we receive from MCP:
    """
    Current MCP Roots (1 total):

    1. test-root
       URI: file:///test/path

    Note: This server demonstrates the roots protocol capability but doesn't actually access files. The roots are provided by the MCP client and can be used by servers that need file system access.
    """

  # ==================== RESOURCES TESTING ====================

  Scenario: List all available resources
    Then the MCP resources contains:
    """
    - uri: test://static/resource/1
      name: Resource 1
      mimeType: text/plain
    - uri: "test://static/resource/2"
      name: "Resource 2"
      mimeType: "application/octet-stream"
    """

  Scenario: Read a static resource text
    When we call the MCP resource "test://static/resource/1"
    Then we receive from MCP:
    """
    Resource 1: This is a plaintext resource
    """

  Scenario: Read a static resource bloc
    When we call the MCP resource "test://static/resource/2"
    Then we receive from MCP:
    """
    UmVzb3VyY2UgMjogVGhpcyBpcyBhIGJhc2U2NCBibG9i
    """


  # ==================== PROMPTS TESTING ====================

  Scenario: List all available prompts
    Then the MCP prompts contains:
    """
    - name: "simple_prompt"
      description: "A prompt without arguments"
    - name: "complex_prompt"
      description: "A prompt with arguments"
      arguments:
      - name: "temperature"
        description: "Temperature setting"
        required: true
      - name: "style"
        description: "Output style"
        required: false
    - name: "resource_prompt"
      description: "A prompt that includes an embedded resource reference"
      arguments:
      - name: "resourceId"
        description: "Resource ID to include (1-100)"
        required: true
    """

  Scenario: Get simple prompt without arguments
    When we call the MCP prompt "simple_prompt"
    Then we receive from MCP exactly:
    """
    role: USER
    content: This is a simple prompt without arguments.
    """

  Scenario: Get complex prompt with arguments
    When we call the MCP prompt "complex_prompt":
    """
    temperature: high
    style: formal
    """
    Then we receive from MCP:
    """
    - role: "USER"
      content: "This is a complex prompt with arguments: temperature=high, style=formal"
    - role: "ASSISTANT"
      content: "I understand. You've provided a complex prompt with temperature and style arguments. How would you like me to proceed?"
    """

  Scenario: Get embedding resource references in prompts
    When we call the MCP prompt "resource_prompt":
    """
    resourceId: "1"
    """
    Then we receive from MCP exactly:
    """
    - role: "USER"
      content: "This prompt includes Resource 1. Please analyze the following resource:"
    - role: "USER"
      content:
        uri: "test://static/resource/1"
        mimeType: "text/plain"
        text: "Resource 1: This is a plaintext resource"
    """

  Scenario: Subscribe to resource updates and receive notifications
    When we subscribe to the MCP resource "test://static/resource/1"
    Then within 10000ms the MCP events list contains:
      """
      - type: "RESOURCES_UPDATE"
        timestamp: ?after {{{[@20 mins ago]}}}
        payload:
        - uri: "test://static/resource/1"
          mimeType: "text/plain"
          text: "Resource 1: This is a plaintext resource"
      """
    Then we unsubscribe from the MCP resource "test://static/resource/1"


  # ==================== ERROR HANDLING ====================

  Scenario: Call non-existent tool
    When we call the MCP tool "nonExistentTool":
    """
    param: value
    """
    Then the MCP response contains an error

  Scenario: Call tool with invalid parameters
    When we call the MCP tool "add":
    """
    a: not_a_number
    b: 5
    """
    Then the MCP response contains an error

  Scenario: Read non-existent resource
    When we call the MCP resource "file:///nonexistent.txt"
    Then the MCP response contains an error

  Scenario: Get non-existent prompt
    When we call the MCP prompt "nonExistentPrompt"
    Then the MCP response contains an error

  # ==================== COMPLEX SCENARIOS ====================

  Scenario: Chain multiple tool calls
    When we call the MCP tool "add":
    """
    a: 10
    b: 5
    """
    Then we receive from MCP:
    """
    The sum of 10 and 5 is 15.
    """
    When we call the MCP tool "add":
    """
    a: 3
    b: 2
    """
    Then we receive from MCP:
    """
    The sum of 3 and 2 is 5.
    """
```

## File: tzatziki-test-mcp/src/test/resources/features/mcp-weather-server.feature
```
Feature: MCP Weather Server Testing

  # ==================== TOOLS TESTING ====================

  Scenario: List available tools
    Then the MCP tools contains exactly:
    """
    - name: "getTemperature"
      description: "Get the temperature (in celsius) for a specific location"
      inputSchema:
        type: "object"
        properties:
          latitude:
            type: "number"
            format: "double"
            description: "The location latitude"
          longitude:
            type: "number"
            format: "double"
            description: "The location longitude"
        required:
        - "latitude"
        - "longitude"
      outputSchema:
        $schema: "https://json-schema.org/draft/2020-12/schema"
        type: "object"
        properties:
          current:
            type: "object"
            properties:
              interval:
                type: "integer"
                format: "int32"
              temperature_2m:
                type: "number"
                format: "double"
              time:
                type: "string"
                format: "date-time"
            required:
            - "interval"
            - "temperature_2m"
            - "time"
        required:
        - "current"
    """

  Scenario: Get temperature for a location
    Given that calling "/weather/v1/forecast?latitude=(.*)&longitude=(.*)&current=temperature_2m" will return:
    """json
    {
      "latitude": 10,
      "longitude": 20,
      "generationtime_ms": 0.0209808349609375,
      "utc_offset_seconds": 0,
      "timezone": "GMT",
      "timezone_abbreviation": "GMT",
      "elevation": 399,
      "current_units": {
        "time": "iso8601",
        "interval": "seconds",
        "temperature_2m": "°C"
        },
      "current": {
        "time": "2025-10-13T16:30",
        "interval": 900,
        "temperature_2m": 28.2
        }
    }
    """

    When we call the MCP tool "getTemperature":
    """
      latitude: 10
      longitude: 20
    """

    Then we receive from MCP:
    """json
    {"current":{"time":"2025-10-13T16:30:00","interval":900,"temperature_2m":28.2}}
    """

    And the MCP events list contains:
      """
      - type: "TOOLS_CHANGE"
        payload:
        - name: "humidity-calculator"
          title: "dummy humidity calculator"
      - type: "PROMPTS_CHANGE"
        payload:
        - name: "humidity prompt"
          description: "Prompt to calculate humidity"
          arguments:
          - name: "location"
            description: "Location for humidity calculation"
            required: true
      - type: "RESOURCES_CHANGE"
        payload:
        - uri: "weather://data/country"
          name: "Country Database"
          description: "Database of supported countries"
          mimeType: "application/json"
      """

  # ==================== RESOURCES TESTING ====================

  Scenario: List available resources
    Then the MCP resources contains:
    """
    - uri: weather://data/cities
      name: Cities Database
      description: Database of supported cities
      mimeType: application/json
    """

  Scenario: Read a specific resource
    When we call the MCP resource "weather://data/cities"
    Then we receive from MCP:
    """yml
    - name: "Paris"
      country: "FR"
      lat: 48.8566
      lon: 2.3522
    - name: "London"
      country: "UK"
      lat: 51.5074
      lon: -0.1278
    - name: "New York"
      country: "US"
      lat: 40.7128
      lon: -74.006
    - name: "Tokyo"
      country: "JP"
      lat: 35.6762
      lon: 139.6503
    - name: "Sydney"
      country: "AU"
      lat: -33.8688
      lon: 151.2093
    """

  # ==================== PROMPTS TESTING ====================

  Scenario: List available prompts
    Then the MCP prompts contains:
    """
    - name: temperature-alert
      description: Create a temperature alert message
    """

  Scenario: Get prompt with arguments
    When we call the MCP prompt "temperature-alert":
    """
    threshold: 30
    location: Paris
    """
    Then we receive from MCP:
    """json
      {"role":"ASSISTANT","content":"Create a temperature alert for Paris when temperature exceeds 30 degrees Celsius."}
    """
```
