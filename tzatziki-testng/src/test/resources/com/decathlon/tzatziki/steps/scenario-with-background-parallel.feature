Feature: a feature with a background that we template from the examples in the scenario

  Background:
    * if 1 == {{{[examples.testValue]}}} => map.property is 1
    * if 2 == {{{[examples.testValue]}}} => map.property is 2
    * if 3 == {{{[examples.testValue]}}} => map.property is 3
    * if 4 == {{{[examples.testValue]}}} => map.property is 4
    * if 5 == {{{[examples.testValue]}}} => map.property is 5
    * if 6 == {{{[examples.testValue]}}} => map.property is 6
    * if 7 == {{{[examples.testValue]}}} => map.property is 7
    * if 8 == {{{[examples.testValue]}}} => map.property is 8
    * if 9 == {{{[examples.testValue]}}} => map.property is 9

  Scenario Template: our examples are stable between threads while running in parallel
    Then map.property is equal to <testValue>

    Examples:
      | testValue |
      | 1         |
      | 2         |
      | 3         |
      | 4         |
      | 5         |
      | 6         |
      | 7         |
      | 8         |
      | 9         |
