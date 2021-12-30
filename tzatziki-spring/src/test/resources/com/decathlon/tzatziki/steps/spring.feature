Feature: to interact with a spring boot service

  Background:
    * we clear all the caches
    * we clear the nameOfTheCache cache

  Scenario: we can query a spring service
    When we call "/hello"
    Then we receive "Hello world!"

  Scenario: we can manage the cache
    Given the cache nameOfTheCache will contain:
      """yml
      key:
        - value
      """

    Then the cache nameOfTheCache contains:
      """yml
      key:
        - value
      """

    And it is not true that the cache nameOfTheCache contains:
      """yml
      key1:
        - value
      key:
        - value1
      """
