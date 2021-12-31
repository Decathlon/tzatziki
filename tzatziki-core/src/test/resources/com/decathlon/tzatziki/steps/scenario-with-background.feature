Feature: a feature with a background that we template from the examples in the scenario

  Background:
    Given that map is a Map:
      """yml
      property: {{{[examples.testValue]}}}
      """
    And if map.property == 4 => map.property is equal to 4

  Scenario Template: test 1 and 2
    Then map.property is equal to <testValue>

    Examples:
      | testValue |
      | 1         |
      | 2         |

  Scenario Template: test 3 and 4
    Then map.property is equal to <testValue>
    And if map.property == 3 => map.property is equal to 3

    Examples:
      | testValue |
      | 3         |
      | 4         |

  Scenario: another scenario that doesn't have examples
    Then map.property is equal to "examples.testValue"
