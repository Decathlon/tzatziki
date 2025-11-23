Feature: MCP Everything Server Testing
  Test all features of the MCP Everything Server via sse transport
  The everything server demonstrates all MCP protocol capabilities

  #TODO
  #notification
  # logging from mcp

    # ==================== TOOLS TESTING ====================

  Scenario: List all available tools from everything server
    Then the tools contains:
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

  Scenario: Call echo tool with simple message
    When we call the tool "echo":
    """
    message: Hello from Tzatziki!
    """
    Then we receive from mcp:
    """
    Echo: Hello from Tzatziki!
    """

  Scenario: Call add tool with numbers
    When we call the tool "add":
    """
    a: 5
    b: 3
    """
    Then we receive from mcp:
    """
    The sum of 5 and 3 is 8.
    """

  Scenario: Call longRunningOperation tool
    When we call the tool "longRunningOperation":
    """
    duration: 1
    steps: 2
    """
    Then we receive from mcp:
    """
    Long running operation completed. Duration: 1 seconds, Steps: 2.
    """

  Scenario: Call sampleLLM tool
    When we call the tool "sampleLLM":
    """
    prompt: What is 2+2?
    maxTokens: 50
    """
    Then we receive from mcp:
    """
    LLM sampling result: Resource sampleLLM context: What is 2+2?
    """

  Scenario: Call getTinyImage tool and get base64 response
    When we call the tool "getTinyImage":
    """
    prompt: What is 2+2?
    maxTokens: 50
    """
    Then we receive from mcp a McpResponse:
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
    When we call the tool "annotatedMessage":
    """
    messageType: error
    includeImage: false
    """
    Then we receive from mcp a McpResponse:
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
    When we call the tool "annotatedMessage":
    """
    messageType: blabla
    includeImage: false
    """
    Then we receive from mcp a McpResponse:
    """
      isError: true
      error:
        - received: "blabla"
          code: "invalid_enum_value"
    """

  Scenario: Call getResourceReference tool and get a resource reference
    When we call the tool "getResourceReference":
    """
    resourceId: 14
    """
    Then we receive from mcp exactly a McpResponse:
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
    When we call the tool "startElicitation":
    """
    color: red
    number: 13
    pets: cat
    """
    Then we receive from mcp a McpResponse:
    """
    content:
      - type: "text"
        payload: "✅ User provided their favorite things!"
      - type: "text"
        payload: "Their favorites are:\n- Color: not specified\n- Number: not specified\n- Pets: not specified"
      - type: "text"
        payload: "\nRaw result: {\n  \"action\": \"accept\",\n  \"content\": {\n    \"message\": \"Eclitation response\"\n  }\n}"
    """

  Scenario: Call structuredContent tool and get response with structuredContent
    When we call the tool "structuredContent":
    """
    location: "Lille"
    """
    Then we receive from mcp a McpResponse:
    """
      structuredContent:
        temperature: 22.5
        humidity: 65
    """

  Scenario: Call listRoots tool to list the current MCP roots
    When we call the tool "listRoots":
    """
    location: "Lille"
    """
    Then we receive from mcp:
    """
    Current MCP Roots (1 total):

    1. test-root
       URI: file:///test/path

    Note: This server demonstrates the roots protocol capability but doesn't actually access files. The roots are provided by the MCP client and can be used by servers that need file system access.
    """

  # ==================== RESOURCES TESTING ====================

  Scenario: List all available resources
    Then the resources contains:
    """
    - uri: test://static/resource/1
      name: Resource 1
      mimeType: text/plain
    - uri: "test://static/resource/2"
      name: "Resource 2"
      mimeType: "application/octet-stream"
    """

  Scenario: Read a static resource text
    When we call the resource "test://static/resource/1"
    Then we receive from mcp:
    """
    Resource 1: This is a plaintext resource
    """

  Scenario: Read a static resource bloc
    When we call the resource "test://static/resource/2"
    Then we receive from mcp:
    """
    UmVzb3VyY2UgMjogVGhpcyBpcyBhIGJhc2U2NCBibG9i
    """


  # ==================== PROMPTS TESTING ====================

  Scenario: List all available prompts
    Then the prompts contains:
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
    When we call the prompt "simple_prompt"
    Then we receive from mcp exactly:
    """
    role: USER
    content: This is a simple prompt without arguments.
    """

  Scenario: Get complex prompt with arguments
    When we call the prompt "complex_prompt":
    """
    temperature: high
    style: formal
    """
    Then we receive from mcp:
    """
    - role: "USER"
      content: "This is a complex prompt with arguments: temperature=high, style=formal"
    - role: "ASSISTANT"
      content: "I understand. You've provided a complex prompt with temperature and style arguments. How would you like me to proceed?"
    """

  Scenario: Get embedding resource references in prompts
    When we call the prompt "resource_prompt":
    """
    resourceId: "1"
    """
    Then we receive from mcp exactly:
    """
    - role: "USER"
      content: "This prompt includes Resource 1. Please analyze the following resource:"
    - role: "USER"
      content:
        uri: "test://static/resource/1"
        mimeType: "text/plain"
        text: "Resource 1: This is a plaintext resource"
    """


  # ==================== ERROR HANDLING ====================

  Scenario: Call non-existent tool
    When we call the tool "nonExistentTool":
    """
    param: value
    """
    Then the response contains an error

  Scenario: Call tool with invalid parameters
    When we call the tool "add":
    """
    a: not_a_number
    b: 5
    """
    Then the response contains an error

  Scenario: Read non-existent resource
    When we call the resource "file:///nonexistent.txt"
    Then the response contains an error

  Scenario: Get non-existent prompt
    When we call the prompt "nonExistentPrompt"
    Then the response contains an error

  # ==================== COMPLEX SCENARIOS ====================

  Scenario: Chain multiple tool calls
    When we call the tool "add":
    """
    a: 10
    b: 5
    """
    Then we receive from mcp:
    """
    The sum of 10 and 5 is 15.
    """
    When we call the tool "add":
    """
    a: 3
    b: 2
    """
    Then we receive from mcp:
    """
    The sum of 3 and 2 is 5.
    """

