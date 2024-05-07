Feature: to interact with a browser

  Background:
    * a root logger set to INFO

  Scenario: we can get an url
    Given that browser navigate to "http://localhost/test"
    Then the logs contain:
      """
      - ?e .* Getting page http://localhost/test
      """
    Then browser url is "http://localhost/test"
    And browser url is "http://localhost/test" within 1000ms
    And the logs contain:
      """
      - ?e .* Waiting page http://localhost/test with timeout 1000ms
      """
    And it is not true that browser url is "http://localhost/failedtest"

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

    And it is not true that browser navigate to "http://localhost/test" waiting "badselect"
