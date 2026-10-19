@db
Feature: Database operations

  Scenario: Database cleaner truncates tables
    Given the test table has data
    Then after cleaning the test table is empty

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
    And product_list contains only:
      | id | name   | price |
      | 1  | Widget | 9.99  |
      | 2  | Gadget | 19.99 |

  Scenario: Assert table contains exactly checks all columns
    Given that the products table will contain:
      | id | name   | price |
      | 1  | Widget | 9.99  |
    Then the products table contains exactly:
      | id | name   | price |
      | 1  | Widget | 9.99  |
    And a java.lang.AssertionError is thrown when the products table contains exactly:
      | id | name   |
      | 1  | Widget |

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

  Scenario: Tables whose names require quoting are cleaned
    Given the "audit-events" table has a row
    And the "MixedCase" table has a row
    Then after cleaning the "audit-events" table is empty
    And after cleaning the "MixedCase" table is empty

  Scenario: Values are converted using the column types of a schema-qualified table
    Given that the inventory.stocks table will contain:
      | id | quantity | available | updated_on | updated_at           |
      | 1  | 5        | true      | 2026-04-28 | 2026-04-28T10:15:30Z |
    Then the inventory.stocks table contains only:
      | id | quantity | available | updated_on | updated_at           |
      | 1  | 5        | true      | 2026-04-28 | 2026-04-28T10:15:30Z |

  Scenario: Tables excluded from cleaning are kept
    Given the test table has data
    Then tables excluded from cleaning keep their data until the filter is reset

  Scenario: Triggers of the default schema can be toggled
    Then triggers of the default schema can be toggled
