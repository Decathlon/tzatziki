Feature: to interact with a spring boot service having a persistence layer

  Scenario: we can query a spring app and manipulate the database states using the table names
    Given that the users table will contain:
      | firstName | lastName |
      | Darth     | Vader    |
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
      - firstName: Darth
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
      - firstName: Darth
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
      | firstName | lastName |
      | Darth     | Vader    |
    And that the User entities will contain at least:
      | firstName | lastName |
      | Han       | Solo     |

    And when we call "/users"
    Then we receive only:
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
      | firstName | lastName |
      | Han       | Solo     |
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
      | firstName | lastName |
      | Darth     | Vader    |
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
      - firstName: Darth
        lastName: Vador updated
        updatedAt: 2020-01-01T00:00:00Z
      """
    Then the users table contains:
      """yml
      - firstName: Darth
        lastName: Vador updated
        updatedAt: 2020-01-01T00:00:00Z
      """
    But if the triggers are enabled
    And that the users table will contain:
      """yml
      - firstName: Darth
        lastName: Vador updated a second time
        updatedAt: 2020-01-01T00:00:00Z
      """
    Then the users table contains:
      """yml
      - firstName: Darth
        lastName: Vador updated a second time
        updatedAt: ?after {{@now}}
      """

  Scenario: we can handle the fact that an entity has a lazy field
    Given that the groups table will contain:
      | name |
      | Sith |
    And that the users table will contain:
      | firstName | lastName | group.id |
      | Darth     | Vader    | 1        |
    Then the groups table contains:
      | id | name |
      | 1  | Sith |

  Scenario: we can get a table content
    Given that the users table will contain only:
      | firstName | lastName |
      | Darth     | Vader    |
      | Han       | Solo     |
    Then usersTableContent is the users table content
    And usersTableContent.size is equal to 2
    And usersTableContent contains only:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
      | 2  | Han       | Solo     |

  Scenario: we can get entities
    Given that the User entities will contain only:
      | firstName | lastName |
      | Darth     | Vader    |
      | Han       | Solo     |
    Then userEntities is the User entities
    And userEntities.size is equal to 2
    And userEntities contains only:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
      | 2  | Han       | Solo     |

  Scenario: we can get a table content ordered
    Given that the users table will contain only:
      | firstName | lastName | birthDate                                         | updatedAt    |
      | Darth     | Vader    | {{{[@41 years before The 19th of october 1977]}}} | {{{[@now]}}} |
      | Han       | Solo     | {{{[@32 years before The 19th of october 1977]}}} | {{{[@now]}}} |
    Then usersTableContent is the users table content ordered by lastName
    And usersTableContent contains only and in order:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |
      | 1  | Darth     | Vader    |
    Then usersTableContent is the users table content ordered by birthDate
    And usersTableContent contains only and in order:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
      | 2  | Han       | Solo     |
    Then usersTableContent is the users table content ordered by birthDate desc
    And usersTableContent contains only and in order:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |
      | 1  | Darth     | Vader    |
    Then usersTableContent is the users table content ordered by updatedAt and birthDate desc
    And usersTableContent contains only and in order:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |
      | 1  | Darth     | Vader    |

  Scenario: we can get entities ordered
    Given that the User entities will contain only:
      | firstName | lastName | birthDate                                         | updatedAt    |
      | Darth     | Vader    | {{{[@41 years before The 19th of october 1977]}}} | {{{[@now]}}} |
      | Han       | Solo     | {{{[@32 years before The 19th of october 1977]}}} | {{{[@now]}}} |
    Then userEntities is the User entities ordered by lastName
    And userEntities contains only and in order:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |
      | 1  | Darth     | Vader    |
    Then userEntities is the User entities ordered by birthDate
    And userEntities contains only and in order:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
      | 2  | Han       | Solo     |
    Then userEntities is the User entities ordered by birthDate desc
    And userEntities contains only and in order:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |
      | 1  | Darth     | Vader    |
    Then userEntities is the User entities ordered by updatedAt and birthDate desc
    And userEntities contains only and in order:
      | id | firstName | lastName |
      | 2  | Han       | Solo     |
      | 1  | Darth     | Vader    |

  Scenario: there shouldn't be any "within" implicit guard in JPA assertions
    Given that after 100ms the User entities will contain only:
      | firstName | lastName |
      | Darth     | Vader    |
    Then it is not true that the User table contains:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
    But within 150ms the User table contains:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |

    # empty the User table
    And if after 100ms the User table will contain only:
      | id | firstName | lastName |
    Then it is not true that the User table contains nothing
    But within 150ms the User table contains nothing

  Scenario: default value should still be asserted if they are present in the assertion (eg: false boolean)
    Given the users table will contain:
      | firstName | lastName |
      | Darth     | Vader    |
    Given that the groups table will contain:
    """
    name: toto_group
    users:
    - id: 1
    """
    Then it is not true that the groups table contains:
    """
    id: 1
    name: null
    users: []
    """
    Given that the evilness table will contain:
      | evil |
      | true |
    Then it is not true that the evilness table contains:
      | id | evil  |
      | 1  | false |

  Scenario: we can use extended entities and manage their tables (ex. super_users extends users)
    Given the super_users table will contain:
      | firstName | lastName  | role  |
      | Darth     | Vader     | admin |
      | Anakin    | Skywalker | dummy |
    Then the super_users table contains:
      | id | firstName | lastName  | role            |
      | 1  | Darth     | Vader     | superUser_admin |
      | 2  | Anakin    | Skywalker | superUser_dummy |

  Scenario: if we have a table which is handled by multiple entities, we should prioritize entity types from default parser package
    # non-default package, should not be used and throw an exception
    Given that an UnrecognizedPropertyException is thrown when the evilness table will contain:
      | badAttribute |
      | true         |
    And the evilness table will contain:
      | evil |
      | true |
    Then the evilness table contains only:
      | id | evil |
      | 1  | true |
    # the non-default package was not inserted
    And it is not true that the evilness table contains:
      | badAttribute |
      | true         |


  Scenario: we can manipulate tables from different schemas
    Given that the books table will contain:
      | title        |
      | Harry Potter |

    And that the products table will contain only:
      | name     |
      | computer |

    Then the books table contains only:
      | id | title        |
      | 1  | Harry Potter |

    And the products table contains only:
      | id | name     |
      | 1  | computer |

  Scenario: we can insert data into parent & child tables
    Given that the groups table will contain:
      | name   |
      | admins |
      | guests |
    And the users table will contain:
      | firstName | lastName | group.id |
      | Chuck     | Norris   | 1        |
      | Uma       | Thurman  | 2        |
      | Jackie    | Chan     | 2        |
    Then the groups table contains:
      | id | name   |
      | 1  | admins |
      | 2  | guests |
    And the users table contains:
      | id | firstName | lastName | group.id | group.name |
      | 1  | Chuck     | Norris   | 1        | admins     |
      | 2  | Uma       | Thurman  | 2        | guests     |
      | 3  | Jackie    | Chan     | 2        | guests     |
    
  Scenario: all schemas are cleared before each scenario

    Then the books table contains nothing

    Then the products table contains nothing