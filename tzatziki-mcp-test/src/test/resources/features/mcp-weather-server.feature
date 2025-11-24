Feature: MCP Weather Server Testing

  # ==================== TOOLS TESTING ====================

  Scenario: List available tools
    Then the tools contains exactly:
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

    When we call the tool "getTemperature":
    """
      latitude: 10
      longitude: 20
    """

    Then we receive from mcp:
    """json
    {"current":{"time":"2025-10-13T16:30:00","interval":900,"temperature_2m":28.2}}
    """

    And the mcp events list contains:
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
    Then the resources contains:
    """
    - uri: weather://data/cities
      name: Cities Database
      description: Database of supported cities
      mimeType: application/json
    """

  Scenario: Read a specific resource
    When we call the resource "weather://data/cities"
    Then we receive from mcp:
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
    Then the prompts contains:
    """
    - name: temperature-alert
      description: Create a temperature alert message
    """

  Scenario: Get prompt with arguments
    When we call the prompt "temperature-alert":
    """
    threshold: 30
    location: Paris
    """
    Then we receive from mcp:
    """json
      {"role":"ASSISTANT","content":"Create a temperature alert for Paris when temperature exceeds 30 degrees Celsius."}
    """


