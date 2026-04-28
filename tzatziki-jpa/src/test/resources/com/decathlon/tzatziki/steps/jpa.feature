Feature: JPA entity operations

  Scenario: Insert entities using "will contain"
    Given that the Item entities will contain:
      | id | name   | price |
      | 1  | Laptop | 999.99 |
      | 2  | Mouse  | 29.99  |
    Then the Item entities contain:
      | id | name   | price  |
      | 1  | Laptop | 999.99 |
      | 2  | Mouse  | 29.99  |

  Scenario: Insert entities with "only" mode truncates first
    Given that the Item entities will contain:
      | id | name    | price |
      | 1  | OldItem | 10.0  |
    And that the Item entities will contain only:
      | id | name    | price |
      | 2  | NewItem | 20.0  |
    Then the Item entities contain:
      | id | name    | price |
      | 2  | NewItem | 20.0  |

  Scenario: Assert entities contain nothing
    Then the Item entities contain nothing

  Scenario: Insert entities with null values
    Given that the Item entities will contain:
      | id | name   | price | description |
      | 1  | NoDesc | 5.00  |             |
    Then the Item entities contain:
      | id | name   | description |
      | 1  | NoDesc |             |

  Scenario: Store entities in a variable
    Given that the Item entities will contain:
      | id | name   | price |
      | 1  | Widget | 15.00 |
    Then items is the Item entities
    And items contains:
      | id | name   |
      | 1  | Widget |

  Scenario: Insert entities with relationship
    Given that the Category entities will contain:
      | id | name        |
      | 1  | Electronics |
    And that the Item entities will contain:
      | id | name   | price  | category.id |
      | 1  | Tablet | 499.99 | 1           |
    Then the Item entities contain:
      | id | name   | category.id | category.name |
      | 1  | Tablet | 1           | Electronics   |

  Scenario: Insert entities with @Transient fields ignored
    Given that the Item entities will contain:
      | id | name   | price | internalNote |
      | 1  | Secret | 0.01  | hidden       |
    Then the Item entities contain:
      | id | name   | price |
      | 1  | Secret | 0.01  |

  Scenario: Table-level steps work via JPA backend
    Given that the items table will contain:
      | id | name  | price |
      | 1  | Chair | 75.0  |
    Then the items table contains:
      | id | name  | price |
      | 1  | Chair | 75.0  |

  Scenario: Table-level "contains nothing" works via JPA backend
    Then the items table contains nothing
