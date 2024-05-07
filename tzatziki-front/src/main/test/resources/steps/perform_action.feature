Feature: to perform an action on browser

  Background:
    * a root logger set to INFO

    #TODO make test failing
  Scenario: we can fill an input
    When we perform a FILL with ("1","2") on "selector1"
    Then the logs contain:
      """
      - ?e .* Finding element selector1
      - ?e .* Action performed FILL with params 1, 2
      """

    When we perform a FILL with ("1","2") on "selector2" waiting "selector3" visible within 100ms
    Then the logs contain:
      """
      - ?e .* Finding element selector2
      - ?e .* Action performed FILL with params 1, 2
      - ?e .* Waiting element with selector selector3, visible true and timeout 100ms
      """

  Scenario: we can get an url and wait for selector
    Given that browser navigate to "http://localhost/test" waiting "selector" visible within 100ms
    And the logs contain:
      """
      - ?e .* Getting page http://localhost/test
      - ?e .* Waiting element with selector selector, visible true and timeout 100ms
      """

    Given that browser navigate to "http://localhost/test" waiting "selector" within 100ms
    And the logs contain:
      """
      - ?e .* Getting page http://localhost/test
      - ?e .* Waiting element with selector selector, visible false and timeout 100ms
      """