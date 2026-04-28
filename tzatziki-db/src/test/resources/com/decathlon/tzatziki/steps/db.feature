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

  # ---- Table-level step definitions (DbBackend) ----

  Scenario: Insert data into a table using "will contain"
    Given that the products table will contain:
      | id | name     | price |
      | 1  | Widget   | 9.99  |
      | 2  | Gadget   | 19.99 |
    Then the products table contains:
      | id | name     | price |
      | 1  | Widget   | 9.99  |
      | 2  | Gadget   | 19.99 |

  Scenario: Insert data with "only" mode truncates existing rows first
    Given that the products table will contain:
      | id | name     | price |
      | 1  | Widget   | 9.99  |
    Given that the products table will contain only:
      | id | name     | price |
      | 3  | Doohickey | 29.99 |
    Then the products table contains:
      | id | name      | price |
      | 3  | Doohickey | 29.99 |

  Scenario: Assert table contains nothing after truncation
    Given that the products table will contain:
      | id | name   | price |
      | 1  | Widget | 9.99  |
    Given that the products table will contain only:
      | id | name | price |
    Then the products table contains nothing

  Scenario: Table content can be stored in a variable
    Given that the products table will contain:
      | id | name   | price |
      | 1  | Widget | 9.99  |
      | 2  | Gadget | 19.99 |
    Then product_list is the products table content

  Scenario: Insert data with null values
    Given that the products table will contain:
      | id | name   | price |
      | 1  | Widget |       |
    Then the products table contains:
      | id | name   |
      | 1  | Widget |

  Scenario: Insert and verify multiple rows with ordering
    Given that the products table will contain:
      | id | name     | price |
      | 3  | Charlie  | 30.00 |
      | 1  | Alpha    | 10.00 |
      | 2  | Bravo    | 20.00 |
    Then the products table contains at least:
      | id | name    | price |
      | 1  | Alpha   | 10.00 |
      | 2  | Bravo   | 20.00 |

  Scenario: Triggers are disabled during insertion
    Given that the triggered_table table will contain:
      | id | name  |
      | 1  | test1 |
    Then the audit_log table contains nothing

  Scenario: Triggers can be re-enabled for insertion
    Given that the triggers are enabled
    Given that the triggered_table table will contain:
      | id | name  |
      | 2  | test2 |
    Then the audit_log table contains:
      | table_name      | operation |
      | triggered_table | INSERT    |
