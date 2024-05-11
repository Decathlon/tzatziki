Feature: to assert element in browser

  Background:
    * a root logger set to INFO

  Scenario: we can assert element on page
    Then the page contains "selector" visible within 100ms
    And the logs contain:
      """
      - ?e .* Waiting element with selector selector, visible true and timeout 100ms
      """
    And it is not true that the page contains "badselect" visible within 100ms

  Scenario: we can assert element on page with attributes
    Then the page contains "selector" visible within 100ms with attributes ("key1":"value1", "key2":"value2", "key3":"value3")
    And the logs contain:
      """
      - ?e .* Waiting element with selector selector, visible true and timeout 100ms
      """
    And it is not true that the page contains "selector" visible within 100ms with attributes ("key1":"badvalue", "key2":"value2", "key3":"value3")

  Scenario: we can assert element on page with styles
    Then the page contains "selector" visible within 100ms with attributes ("key1":"value1") and with style ("style1":"styleValue1", "style2":"styleValue2")
    And the logs contain:
      """
      - ?e .* Waiting element with selector selector, visible true and timeout 100ms
      """
    And it is not true that the page contains "selector" visible within 100ms with attributes ("key1":"value1") and with style ("style1":"styleValue1", "style2":"badValue")