@db
Feature: Database operations

  Scenario: Database cleaner truncates tables
    Given the test table has data
    Then after cleaning the test table is empty

  Scenario: Triggers can be disabled and enabled
    Given the triggers are disabled
    Then triggers are disabled on the test table
    Given the triggers are enabled
    Then triggers are enabled on the test table
