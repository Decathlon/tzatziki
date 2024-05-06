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