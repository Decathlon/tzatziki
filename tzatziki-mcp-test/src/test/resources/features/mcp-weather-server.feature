Feature: MCP Weather Server Testing

  Scenario: List available tools
    Then the tools contains:
    """
    - name: getTemperature
      description: Get the temperature (in celsius) for a specific location
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

      When we calls the tool "getTemperature":
      """
        latitude: 10
        longitude: 20
      """

      Then we receive from tool:
      """json
      {"current":{"time":"2025-10-13T16:30:00","interval":900,"temperature_2m":28.2}}
      """

