Feature: to perform an action on browser

  Background:
    * a root logger set to INFO

  Scenario: we can fill an input
    When we perform a FILL with ("1","2") on "selector1"
    Then the logs contain:
      """
      - ?e .* Finding element selector1
      - ?e .* HTMLElement with id id1 Action performed FILL with params 1, 2
      - ?e .* HTMLElement with id id2 Action performed FILL with params 1, 2
      """

    And it is not true that we perform a FILL with ("1","2") on "badselect"

    When we perform a FILL with ("1","2") on "selector2" waiting "selector3" visible within 100ms
    Then the logs contain:
      """
      - ?e .* Finding element selector2
      - ?e .* HTMLElement with id id1 Action performed FILL with params 1, 2
      - ?e .* HTMLElement with id id2 Action performed FILL with params 1, 2
      - ?e .* Waiting element with selector selector3, visible true and timeout 100ms
      """

    And it is not true that we perform a FILL with ("1","2") on "selector2" waiting "badselect" visible within 100ms