Feature: to interact with a spring boot service having a persistence layer

  Scenario: we can query a spring app and manipulate the database states using the table names
    Given that the users table will contain:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
    When we call "/users/1"
    Then we receive:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      """
    And the users table contains:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
    But if we delete "/users/1"
    Then the users table contains nothing
    And calling "/users/1" returns a status NOT_FOUND_404

  Scenario: we can query a spring app using and manipulate the database states using the repository names
    Given that the UserDataSpringRepository repository will contain:
      """yml
      - id: 1
        firstName: Darth
        lastName: Vader
      """
    And when we call "/users/1"
    Then we receive:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      """
    And the UserDataSpringRepository repository contains:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      """
    But if we delete "/users/1"
    Then the UserDataSpringRepository repository contains nothing

  Scenario: we can query a spring app using and manipulate the database states using the Entity names
    Given that the User entities will contain:
      """yml
      - id: 1
        firstName: Darth
        lastName: Vader
      """

    And when we call "/users/1"
    Then we receive:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      """
    And the User entities contain:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      """
    But if we delete "/users/1"
    Then the User entities contain nothing

  Scenario: we can control if the table contains at least or only some entities
    Given that the User entities will contain:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
    And that the User entities will contain at least:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |

    And when we call "/users"
    Then we receive exactly:
      """yml
      - id: 1
        firstName: Darth
        lastName: Vader
        birthDate: null
        updatedAt: null
        group: null
      - id: 2
        firstName: Han
        lastName: Solo
        birthDate: null
        updatedAt: null
        group: null
      """
    But when the User entities will contain only:
      | id | firstName | lastName |
      | 1  | Han       | Solo     |
    Then calling "/users" returns exactly:
      """yml
      - id: 1
        firstName: Han
        lastName: Solo
        birthDate: null
        updatedAt: null
        group: null
      """
    And the users table contains only:
      """yml
      - id: 1
        firstName: Han
      """

  Scenario: we can assert that a column is null using implicitely an anonymous object if the content matches a flag
    Given that the users table will contain:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
    Then the users table contains:
      | id | birthDate |
      | 1  | ?isNull   |
    And the users table contains:
      """yml
      - id: 1
        birthDate: ?isNull
      """
    And the users table contains:
      """json
      {"id": 1, "birthDate": "?isNull"}
      """

  Scenario: we can disable or enable triggers so that we can insert the test data we want
    When the users table will contain:
      """yml
      - id: 1
        firstName: Darth
        lastName: Vador updated
        updatedAt: 2020-01-01T00:00:00Z
      """
    Then the users table contains:
      """yml
      - id: 1
        firstName: Darth
        lastName: Vador updated
        updatedAt: 2020-01-01T00:00:00Z
      """
    But if the triggers are enabled
    And that the users table will contain:
      """yml
      - id: 1
        firstName: Darth
        lastName: Vador updated a second time
        updatedAt: 2020-01-01T00:00:00Z
      """
    Then the users table contains:
      """yml
      - id: 1
        firstName: Darth
        lastName: Vador updated a second time
        updatedAt: ?after {{@now}}
      """

  Scenario: we can handle the fact that an entity has a lazy field
    Given that the groups table will contain:
      | id | name |
      | 1  | Sith |
    And that the users table will contain:
      | id | firstName | lastName | group.id |
      | 1  | Darth     | Vader    | 1        |
    Then the groups table contains:
      | id | name |
      | 1  | Sith |

  Scenario: we can get a table content
    Given that the users table will contain only:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
      | 2  | Han       | Solo     |
    Then usersTableContent is the users table content
    And usersTableContent.size is equal to 2
    And usersTableContent[0].id is equal to 1
    And usersTableContent[1].id is equal to 2

  Scenario: we can get entities
    Given that the User entities will contain only:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
      | 2  | Han       | Solo     |
    Then userEntities is the User entities
    And userEntities.size is equal to 2
    And userEntities[0].id is equal to 1
    And userEntities[1].id is equal to 2

  Scenario: we can handle multiple datasources
    Given that the dataSource is productDataSource
    And that the products table will contain:
      | id | name  | price |
      | 1  | Darth | 50    |
    Then the products table contains:
      | id | name  | price |
      | 1  | Darth | 50    |