Tzatziki MCP Test Library
======

## Description

This module provides steps to interact with and test [MCP (Model Context Protocol)](https://modelcontextprotocol.io/) servers.
It allows you to list available tools, resources, and prompts, call them, assert the responses, and receive event notifications.

It is agnostic of the language used to implement the MCP server (Java, Python, TypeScript, etc.), as long as it speaks the MCP protocol.

You can find concrete setup and example tests in the [test folder](src/test) of this module.

## Get started with this module

You need to add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-test-mcp</artifactId>
    <version>x.x.x</version>
    <scope>test</scope>
</dependency>
```

### Configuration

Before running your tests, you need to configure the `McpClientTransport` that will be used to communicate with your MCP server.
Example using `HttpClientStreamableHttpTransport` (for HTTP servers):

```java
import com.decathlon.tzatziki.config.McpClientConfiguration;
import io.cucumber.java.Before;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;

public class MySteps {

    @Before(order = -1)
    public void setup() {
        McpClientConfiguration.setMcpClientTransport(
            HttpClientStreamableHttpTransport.builder("http://localhost:8080").build()
        );
    }
}
```

Example using `StdioClientTransport` (for local processes, e.g. Python, Node.js):

```java
import com.decathlon.tzatziki.config.McpClientConfiguration;
import io.cucumber.java.Before;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;

public class MySteps {

    @Before(order = -1)
    public void setup() {
        McpClientConfiguration.setMcpClientTransport(
                new StdioClientTransport(ServerParameters.builder("npx")
                        .args("-y", "@modelcontextprotocol/server-everything", "stdio").build(), 
                        McpJsonMapper.createDefault())
        );
    }
}
```

Example using Spring Boot Test (e.g. Spring AI MCP):

```java
import com.decathlon.tzatziki.config.McpClientConfiguration;
import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = MyMcpServerApplication.class)
public class MyMcpServerSteps {

    @LocalServerPort
    private Integer serverPort;

    @Before
    public void setup() {
        McpClientConfiguration.setMcpClientTransport(
            HttpClientStreamableHttpTransport.builder("http://localhost:" + serverPort).build()
        );
    }
}
```

### Advanced Configuration

You can configure advanced capabilities like Sampling, Elicitation, and Roots by setting the corresponding static fields in `McpClientConfiguration`.

#### Sampling (LLM Generation)

If the MCP server needs to request LLM sampling (generation) from the client (e.g. "human in the loop" or client-side LLM), you can provide a `samplingHandler`.

```java
McpClientConfiguration.setSamplingHandler(request ->
                    Mono.just(new McpSchema.CreateMessageResult(McpSchema.Role.ASSISTANT, request.messages().get(0).content(), "test-model",
                            McpSchema.CreateMessageResult.StopReason.END_TURN)));
```

#### Elicitation (User Input)

If the MCP server needs to ask the user for input or clarification, you can provide an `elicitationHandler`.

```java
McpClientConfiguration.setElicitationHandler(request -> Mono.just(McpSchema.ElicitResult.builder()
        .message(McpSchema.ElicitResult.Action.ACCEPT)
        .content(Map.of("message", "elicitation response"))
        .build()));
```

#### Roots

You can define the roots (directories or resources) that the client exposes to the server. This is often used to define the workspace boundaries for the server.

```java
McpClientConfiguration.setRoots(List.of(
    new McpSchema.Root("file:///path/to/project", "Project Root")
));
```

### Using Testcontainers (Optional)

If you're testing with an MCP server running in a Docker container (as shown in the test examples), you'll need to add the Testcontainers dependency:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>2.0.3</version>
    <scope>test</scope>
</dependency>
```

Example using GenericContainer:

```java
import org.testcontainers.containers.GenericContainer;

private static GenericContainer<?> mcpContainer;

@Before(order = -1)
public void startContainer() {
    if (mcpContainer == null || !mcpContainer.isRunning()) {
        mcpContainer = new GenericContainer<>("your-mcp-server-image")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/").forStatusCode(200));
        mcpContainer.start();
        
        String host = "http://" + mcpContainer.getHost() + ":" + mcpContainer.getMappedPort(8080);
        McpClientConfiguration.setMcpClientTransport(
            HttpClientStreamableHttpTransport.builder(host).build()
        );
    }
}
```

## Usage

### Listing Capabilities

You can assert the list of available tools, resources, or prompts exposed by the MCP server.

```gherkin
# Check available tools with their schema
Then the tools contains:
  """
  - name: "getTemperature"
    description: "Get the temperature"
    inputSchema:
      type: "object"
      properties:
        latitude:
          type: "number"
        longitude:
          type: "number"
      required:
      - "latitude"
      - "longitude"
  """

# Check available resources
Then the resources contains:
  """
  - name: "config"
    uri: "file:///config.json"
  """

# Check available prompts with their arguments
Then the prompts contains:
  """
  - name: "summarize"
    description: "Summarize text"
    arguments:
    - name: "text"
      description: "The text to summarize"
      required: true
  """
```

### Calling Tools, Resources, and Prompts

You can call a tool, resource, or prompt and assert the response.
Arguments can be provided in JSON or YAML format.

#### Calling a Tool

```gherkin
When we call the tool "getTemperature":
  """
  latitude: 48.8566
  longitude: 2.3522
  """
Then we receive from mcp:
  """json
  {
    "current": {
      "temperature_2m": 20.5
    }
  }
  """
```

If the tool takes no arguments:

```gherkin
When we call the tool "printEnv"
Then we receive from mcp:
  """
  ENV_VAR=value
  """
```

You can also pass metadata (like `progressToken`) in the `request-meta` field:

```gherkin
When we call the tool "longRunningOperation":
  """
  request-meta:
    progressToken: my-progress-token
  duration: 10
  """
Then we receive from mcp:
  """
  Operation completed.
  """
```

#### Calling a Resource

```gherkin
When we call the resource "weather://data/cities"
Then we receive from mcp:
  """yml
  - name: "Paris"
    country: "FR"
  """
```

#### Calling a Prompt

```gherkin
When we call the prompt "summarize":
  """
  text: "History"
  """
Then we receive from mcp:
  """json
  {"role":"ASSISTANT","content":"Help me to summarize History..."}
  """
```

### Asserting Responses

You can assert the response content using `Then we receive from mcp:`.
If you want to assert the full `McpResponse` object (including error status, etc.), you can use `Then we receive from mcp a McpResponse:`.

```gherkin
Then we receive from mcp a McpResponse:
  """
  isError: false
  content:
    - type: "text"
      text: "The temperature is 20°C"
    - type: image
        annotations: {}
        payload: ?notNull   
  """
```

### Handling Errors

You can check if the response contains an error.

```gherkin
Then the response contains an error
```

### Subscriptions

You can subscribe and unsubscribe from resources.

```gherkin
When we subscribe to the resource "weather://alerts"
When we unsubscribe from the resource "weather://alerts"
```

### Events

You can assert that specific MCP events have occurred. The following events are captured:

*   `TOOLS_CHANGE`: When the list of available tools changes.
*   `RESOURCES_CHANGE`: When the list of available resources changes.
*   `RESOURCES_UPDATE`: When a subscribed resource is updated.
*   `PROMPTS_CHANGE`: When the list of available prompts changes.
*   `LOGGING`: When the server sends a log message.
*   `PROGRESS`: When the server sends a progress update.

```gherkin
Then the mcp events contains:
  """
  - type: "TOOLS_CHANGE"
    payload:
      - name: "new-tool"
  - type: "LOGGING"
    payload:
      level: "INFO"
      data: "Server started"
  """
```
