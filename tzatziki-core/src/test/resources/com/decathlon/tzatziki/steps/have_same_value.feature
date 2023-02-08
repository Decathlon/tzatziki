Feature: Check that two decimal values have the same value

  Scenario: Check that ending 0 are ignored in value
    Given that something is:
    """
          my_value: 5.0
    """
    Then something is equal to:
    """
          my_value: ?hasDecimalValue 5
    """
  Scenario: Check that ending 0 are ignored in flag
    Given that something is:
    """
          my_value: 9.001
    """
    Then something is equal to:
    """
          my_value: ?hasDecimalValue 9.001000
    """
  Scenario: Compare values of 0 with ending 0
    Given that something is:
    """
          my_value: 0
    """
    Then something is equal to:
    """
          my_value: ?hasDecimalValue 0.00
    """
  Scenario: Check that trilling 0 are ignored in value
    Given that something is:
    """
          my_value: 05
    """
    Then something is equal to:
    """
          my_value: ?hasDecimalValue 5
    """
  Scenario: Check that trilling 0 are ignored in flag
    Given that something is:
    """
          my_value: 9.001
    """
    Then something is equal to:
    """
          my_value: ?hasDecimalValue 09.001
    """
  Scenario: Compare values of 0 with trilling 0
    Given that something is:
    """
          my_value: 00.00
    """
    Then something is equal to:
    """
          my_value: ?hasDecimalValue 0.00
    """
