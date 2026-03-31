Feature: to interact with a kafka broker using plain kafka clients (no Spring dependency)

  Background:
    * this avro schema:
      """yml
      type: record
      name: user
      fields:
        - name: id
          type: int
        - name: name
          type: string
      """

  Scenario: we can publish and assert an avro message on a kafka topic
    When this user is published on the avro-users topic:
      """yml
      id: 1
      name: bob
      """
    Then the avro-users topic contains a user:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can publish a list of avro messages on a kafka topic
    When these users are published on the avro-users-list topic:
      | id | name |
      | 1  | bob  |
      | 2  | lisa |
    Then the avro-users-list topic contains 2 users

  Scenario: we can publish and assert a json message on a kafka topic
    When this json message is published on the json-users topic:
      """yml
      id: 1
      name: bob
      """
    Then the json-users topic contains a json message:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can publish a json message with key on a kafka topic
    When this json message is published on the json-users-with-key topic:
      """yml
      headers:
        uuid: some-id
      key: my-key
      value:
        id: 1
        name: bob
      """
    Then the json-users-with-key topic contains a json message:
      """yml
      headers:
        uuid: some-id
      key: my-key
      value:
        id: 1
        name: bob
      """

  Scenario: we can publish an avro message with headers on a kafka topic
    When this user is published on the avro-users-with-headers topic:
      """yml
      headers:
        trace-id: abc-123
      value:
        id: 1
        name: bob
      """
    Then the avro-users-with-headers topic contains a user:
      """yml
      headers:
        trace-id: abc-123
      value:
        id: 1
        name: bob
      """

  Scenario: we can publish an avro message with a null header value
    When this user is published on the avro-users-null-header topic:
      """yml
      headers:
        trace-id: null
      value:
        id: 1
        name: bob
      """
    Then the avro-users-null-header topic contains a user:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can assert a topic contains 0 messages
    Then the empty-topic topic contains 0 users

  Scenario: we can publish and assert using avro key messages
    Given this avro schema:
      """yml
      type: record
      name: userKey
      fields:
        - name: id
          type: int
      """
    When this user with key userKey is published on the avro-key-users topic:
      """yml
      headers:
        trace-id: key-test
      key:
        id: 42
      value:
        id: 1
        name: bob
      """
    Then the avro-key-users topic contains a user:
      """yml
      headers:
        trace-id: key-test
      value:
        id: 1
        name: bob
      """

  Scenario: we can publish an avro message with nested record
    Given this avro schema:
      """yml
      type: record
      name: userWithAddress
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: address
          type:
            type: record
            name: address
            fields:
              - name: street
                type: string
              - name: city
                type: string
      """
    When this userWithAddress is published on the avro-users-nested topic:
      """yml
      id: 1
      name: bob
      address:
        street: 123 Main St
        city: Springfield
      """
    Then the avro-users-nested topic contains a userWithAddress:
      """yml
      id: 1
      name: bob
      address:
        street: 123 Main St
        city: Springfield
      """

  Scenario: we can publish an avro message with an array field
    Given this avro schema:
      """yml
      type: record
      name: userWithTags
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: tags
          type:
            type: array
            items: string
      """
    When this userWithTags is published on the avro-users-array topic:
      """yml
      id: 1
      name: bob
      tags:
        - developer
        - admin
      """
    Then the avro-users-array topic contains a userWithTags:
      """yml
      id: 1
      name: bob
      tags:
        - developer
        - admin
      """

  Scenario: we can publish an avro message with an enum field
    Given this avro schema:
      """yml
      type: record
      name: userWithRole
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: role
          type:
            type: enum
            name: Role
            symbols:
              - ADMIN
              - USER
              - GUEST
      """
    When this userWithRole is published on the avro-users-enum topic:
      """yml
      id: 1
      name: bob
      role: ADMIN
      """
    Then the avro-users-enum topic contains a userWithRole:
      """yml
      id: 1
      name: bob
      role: ADMIN
      """

  Scenario: we can use template variables in topic names
    Given that topicName is "template-test-topic"
    When this json message is published on the {{topicName}} topic:
      """yml
      id: 1
      name: bob
      """
    Then the {{topicName}} topic contains a json message:
      """yml
      id: 1
      name: bob
      """

  Scenario: offset isolation between tests - first scenario publishes
    When this json message is published on the json-output topic:
      """yml
      id: 1
      name: first-test
      """
    Then the json-output topic contains a json message:
      """yml
      id: 1
      name: first-test
      """

  Scenario: offset isolation between tests - second scenario sees only its own messages
    When this json message is published on the json-output topic:
      """yml
      id: 2
      name: second-test
      """
    Then the json-output topic contains a json message:
      """yml
      id: 2
      name: second-test
      """
    And the json-output topic contains 1 json message

  Scenario: we can assert topic content with contains only comparison
    When this json message is published on the json-contains-only topic:
      """yml
      id: 1
      name: bob
      """
    Then the json-contains-only topic contains only a json message:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can publish and assert from the beginning of a topic
    When this json message is published on the json-from-beginning topic:
      """yml
      id: 1
      name: bob
      """
    Then from the beginning the json-from-beginning topic contains a json message:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can seek to the end of a topic and only see new messages
    When this json message is published on the seek-end-topic topic:
      """yml
      id: 1
      name: before-seek
      """
    Given we seek to the end of the seek-end-topic topic
    When this json message is published on the seek-end-topic topic:
      """yml
      id: 2
      name: after-seek
      """
    Then the seek-end-topic topic contains a json message:
      """yml
      id: 2
      name: after-seek
      """
    Then the seek-end-topic topic contains 1 json message

  Scenario: we can seek to the beginning of a topic and see all messages
    When this json message is published on the seek-begin-topic topic:
      """yml
      id: 1
      name: first
      """
    When this json message is published on the seek-begin-topic topic:
      """yml
      id: 2
      name: second
      """
    Given we seek to the beginning of the seek-begin-topic topic
    Then from the beginning the seek-begin-topic topic contains a json message:
      """yml
      - id: 1
        name: first
      - id: 2
        name: second
      """
