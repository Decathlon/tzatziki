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
        - field_a: value_a
          field_b: value_b
      """

    Then the cache nameOfTheCache contains:
      """yml
      key:
        - field_a: value_a
      """

    Then the cache nameOfTheCache contains exactly:
      """yml
      key:
        - field_a: value_a
          field_b: value_b
      """

    And it is not true that the cache nameOfTheCache contains exactly:
      """yml
      key:
        - field_a: value_a
      """

    And it is not true that the cache nameOfTheCache contains:
      """yml
      key1:
        - field_a: value_a
      key:
        - field_a: value_b
      """

  Scenario Template: we can mock a real url
    Given that calling "http://backend/greeting" will return "Hello from another backend"
    Then calling "<endpoint>" returns "Hello from another backend"
    But if we disable the HttpInterceptor
    Then calling "<endpoint>" returns a status 500

    Examples:
      | endpoint                                 |
      | /rest-template-remote-hello              |
      | /web-client-remote-hello                 |
      | /web-client-builder-remote-hello         |
      | /web-client-from-builder-remote-hello    |

  @ignore
  Scenario: we can still reach the internet
    When we call "http://www.google.com"
    Then we receive a status 200
    But if calling "http://www.google.com" will return a status FORBIDDEN_403
    Then calling "http://www.google.com" returns a status FORBIDDEN_403

  Scenario: we should use Spring Context's mapper PropertyNamingStrategy by default (snake_case)
    Then it is not true that a JsonMappingException is thrown when myPojo is a NonSnakeCasePojo:
    """
    non_snake_case_field: hello
    """

  Scenario: we can get an application context bean through "_application" ObjectSteps' context variable
    Given that helloController is a HelloController "{{{[_application.getBean({{{HelloController}}})]}}}"
    And that helloResponse is "{{{[helloController.hello()]}}}"
    Then helloResponse.body is equal to "Hello world!"

  Scenario: we start an infinite task if clear thread pool executor is enabled
    Given the thread pool executor is cleaned between test runs
    And that we start an infinite task

  Scenario: then the infinite task has been cancelled
    Then infinite task has been shutdown

  Scenario: we start an infinite task if clear thread pool executor is disabled
    Given the thread pool executor is not cleaned between test runs
    And that we start an infinite task

  Scenario: then the infinite task has not been cancelled
    Then it is not true that infinite task has been shutdown