Feature: to interact with the logger

  Scenario: we can set the log level to OFF
    Given a root logger set to OFF
    When we log as INFO:
      """
      some log lines
      """
    Then the logs are empty

  Scenario Template: we can assert the content of the logs
    Given a root logger set to <level>
    When something logs as ERROR:
      """
      some log lines that should be there
      """
    Then if <level> == INFO => the logs contain:
      """
      - ?e .* some [^ ]+ lines that should be there
      """
    But if <level> == OFF => the logs are empty

    Examples:
      | level |
      | INFO  |
      | OFF   |

  Scenario: we can assert that the logs do not contain something
    Given a root logger set to INFO
    When we log as INFO:
      """
      some log lines that should be there
      """
    Then it is not true that the logs contain:
      """
      - ?e .* some [^ ]+ lines that should not be there
      """

  Scenario: we can set the log level of a specific class
    Given a com.decathlon.tzatziki.steps logger set to DEBUG
    When we log as DEBUG:
      """
      some lines
      """
    Then the logs contain:
      """
      - ?e .* some lines
      """

  Scenario Template: we can assert the content of the logs (log in JSON)
    Given a root logger set to <level>
    And the logs are formatted in json
    When something logs as ERROR:
      """
      some log lines that should be there
      """
    Then if <level> == INFO => the logs contain:
      """
      - ?e .*"message":"some [^ ]+ lines that should be there","logger_name":"com.decathlon.tzatziki.steps.LoggerSteps","thread_name":"main","level":"ERROR".*
      """
    But if <level> == OFF => the logs are empty

    Examples:
      | level |
      | INFO  |
      | OFF   |

  Scenario: we can assert that lines are in a given order
    Given a root logger set to INFO
    When we log as INFO:
      """
      this is the first line
      """

    And we log as INFO:
    """
    this is the second line
    """

    Then the logs contain:
      """
      - ?e .*this is the second line.*
      - ?e .*this is the first line.*
      """

    And it is not true that the logs contain in order:
      """
      - ?e .*this is the second line.*
      - ?e .*this is the first line.*
      """

    And the logs contain in order:
      """
      - ?e .*this is the first line.*
      - ?e .*this is the second line.*
      """

  Scenario: there shouldn't be any "within" implicit guard in logger response assertions
    When after 500ms something logs as ERROR:
      """
      some log lines that should be there
      """
    Then it is not true that the logs contain:
      """
      - ?e .* some [^ ]+ lines that should be there
      """
    But within 600ms the logs contain:
      """
      - ?e .* some [^ ]+ lines that should be there
      """