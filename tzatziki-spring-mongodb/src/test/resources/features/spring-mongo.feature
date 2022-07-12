Feature: Interact with a spring boot application that uses mongodb as a persistence layer

  Background:
    * the current time is 2021-01-01T00:00:00Z

  Scenario: manipulate the collection states using the document names (simple document structure)
    Given that the users document will contain only:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
    When we call "/users/1"
    Then we receive:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      """
    And the users document contains:
      | id | firstName | lastName |
      | 1  | Darth     | Vader    |
    But if we delete "/users/1"
    Then the users document contains nothing
    And calling "/users/1" returns a status NOT_FOUND_404

  Scenario: manipulate the collection states using the document names (complex document structure)
    Given that the orders document will contain only:
      """yml
      id: 1
      customer:
        firstName: Darth
      items:
        - name: Peppina
          type: Pizza
          quantity: 2
          price: 11.99
        - name: Savoyarde
          type: Pizza
          quantity: 1
          price: 10.99
      price: 34.97
      """
    When we call "/orders/1"
    Then we receive:
      """yml
      id: 1
      customer:
        firstName: Darth
        lastName: ?isNull
      items:
        - name: Peppina
          type: Pizza
          quantity: 2
          price: 11.99
      price: 34.97
      """
    But if we delete "/orders/1"
    Then the orders document contains nothing

  Scenario: manipulate the collection states using the repository names
    Given that the UserRepository repository will contain only:
      """yml
      - id: 1
        firstName: Darth
        lastName: Vader
        birthDate: 2020-07-02T00:00:00Z
        creationDate: 2022-07-02T00:00:00Z
        lastUpdateDate: 2022-07-02T00:00:00Z
      """
    And when we call "/users/1"
    Then we receive exactly:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      birthDate: ?before {{@now}}
      creationDate: ?after {{@now}}
      lastUpdateDate: ?after {{@now}}
      """
    And the UserRepository repository contains exactly:
      """yml
      id: 1
      firstName: Darth
      lastName: Vader
      birthDate: 2020-07-02T00:00:00Z
      creationDate: 2022-07-02T00:00:00Z
      lastUpdateDate: 2022-07-02T00:00:00Z
      """
    But if we delete "/users/1"
    Then the UserRepository repository contains nothing

  Scenario: manipulate the collection states using the entity names
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
