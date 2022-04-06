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

  Scenario Template: we can mock a real url
    Given that calling "http://backend/greeting" will return "Hello from another backend"
    Then calling "<endpoint>" returns "Hello from another backend"
    But if we disable the HttpInterceptor
    Then calling "<endpoint>" returns a status 500

    Examples:
      | endpoint                                 |
      | /rest-template-remote-hello              |
      | /rest-template-builder-remote-hello      |
      | /rest-template-from-builder-remote-hello |
      | /web-client-remote-hello                 |
      | /web-client-builder-remote-hello         |
      | /web-client-from-builder-remote-hello    |

  Scenario: we can still reach the internet
    When we call "http://www.google.com"
    Then we receive a status 200
    But if calling "http://www.google.com" will return a status FORBIDDEN_403
    Then calling "http://www.google.com" returns a status FORBIDDEN_403

  Scenario: we should use Spring Context's mapper PropertyNamingStrategy by default
    Then myPojo is a NonSnakeCasePojo:
    """
    non_snake_case_field: hello
    """