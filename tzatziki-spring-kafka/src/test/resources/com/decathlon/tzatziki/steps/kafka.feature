Feature: to interact with a spring boot service having a connection to a kafka queue

  Background:
    * a com.decathlon logger set to DEBUG
    Given this avro schema:
      """yml
      type: record
      name: user
      fields:
        - name: id
          type: int
        - name: name
          type: string
      """

  Scenario: we can push an avro message in a kafka topic where a listener expect a simple payload
    When this user is consumed from the users topic:
      """yml
      id: 1
      name: bob
      """
    Then we have received 1 message on the topic users

  Scenario: we can push a list of avro messages as a table in a kafka topic where a listener expect a simple payload
    When these users are consumed from the users topic:
      | id | name |
      | 1  | bob  |
      | 2  | lisa |
    Then we have received 2 messages on the topic users

  Scenario: we can push a json message in a kafka topic where a listener expect a simple payload
    When this json message is consumed from the json-users-input topic:
      """yml
      id: 1
      name: bob
      """
    Then we have received 1 message on the topic json-users-input

  Scenario: we can push a message in a kafka topic where a listener expects a list of payload, topic, partition, offset
    When these users are consumed from the users-with-headers topic:
      """yml
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 1
        name: bob
      - id: 2
        name: lisa
      """
    Then we have received 12 messages on the topic users-with-headers

  Scenario: we can push a message with a key in a kafka topic
    When these users are consumed from the users-with-key topic:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key: a-key
      """
    Then we have received 1 messages on the topic users-with-key
    And the logs contain:
      """yml
      - "?e .*received user with messageKey a-key on users-with-key-0@0: \\{\"id\": 1, \"name\": \"bob\"}"
      """

  Scenario Template: replaying a topic should only be replaying the messages received in this test
    When this user is consumed from the users-with-headers topic:
      """yml
      id: 3
      name: tom
      """
    Then we have received 1 message on the topic users-with-headers

    And if we empty the logs
    And that we replay the topic users-with-headers from <from> with a <method>

    Then within 10000ms we have received 2 messages on the topic users-with-headers
    And the logs contain:
      """yml
      - "?e .*received user on users-with-headers-0@0: \\{\"id\": 3, \"name\": \"tom\"}"
      """
    # these messages are from the previous test and shouldn't leak
    But it is not true that the logs contain:
      """yml
      - "?e .*received user on users-with-headers-\\d@\\d+: \\{\"id\": 1, \"name\": \"bob\"}"
      - "?e .*received user on users-with-headers-\\d@\\d+: \\{\"id\": 2, \"name\": \"lisa\"}"
      """

    Examples:
      | from          | method   |
      | the beginning | listener |
      | the beginning | consumer |
      | offset 0      | listener |
      | offset 0      | consumer |

  Scenario Outline: we can set the offset of a given group-id on a given topic
    When these users are consumed from the users-with-headers topic:
      """yml
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 3
        name: tom
      """
    Then we have received 3 messages on the topic users-with-headers

    But if the current offset of <group-id> on the topic users-with-headers is 2
    And if <group-id> == users-with-headers-group-id-replay => we resume replaying the topic users-with-headers

    Then within 10000ms we have received 4 messages on the topic users-with-headers
    Examples:
      | group-id                           |
      | users-with-headers-group-id        |
      | users-with-headers-group-id-replay |

  Scenario: we can use an avro schema having nested records
    Given this avro schema:
      """yml
      type: record
      name: User
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: group
          type:
            name: Group
            type: record
            fields:
              - name: id
                type: int
              - name: name
                type: string
      """
    And that we receive this user on the topic users-with-group:
      """yml
      id: 1
      name: bob
      group:
        id: 1
        name: minions
      """
    Then we have received 1 message on the topic users-with-group

  Scenario: we can use an avro schema having containing arrays
    Given this avro schema:
      """yml
      type: record
      name: Group
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: users
          type:
            type: array
            items:
              name: User
              type: record
              fields:
                - name: id
                  type: int
                - name: name
                  type: string
      """
    And this Group is consumed from the group-with-users topic:
      """yml
      id: 1
      name: minions
      users:
        - id: 1
          name: bob
      """
    Then we have received 1 message on the topic group-with-users

  Scenario: we can use an avro schema having containing enum
    Given this avro schema:
      """yml
      type: record
      name: Group
      fields:
        - name: id
          type: int
        - name: name
          type: string
        - name: an_enum
          type:
            type: enum
            name: AnEnum
            symbols:
              - FIRST
              - SECOND
              - THIRD
      """
    And this Group is consumed from the group-with-users topic:
      """yml
      id: 1
      name: minions
      an_enum: FIRST
      """
    Then we have received 1 message on the topic group-with-users

  Scenario Template: we can assert that a message has been sent on a topic (repeatedly)
    When this user is published on the exposed-users topic:
      """yml
      id: 1
      name: bob
      """
    Then if <consume> == true => the exposed-users topic contains only this user:
      """yml
      id: 1
      name: bob
      """
    And the exposed-users topic contains 1 message

    Examples:
      | consume |
      | false   |
      | true    |
      | false   |
      | true    |
      | true    |

  Scenario: we can set and assert the headers of a message sent to a topic
    When this user is published on the exposed-users topic:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key: a-key
      """
    Then the exposed-users topic contains this user:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key: a-key
      """
    And the exposed-users topic contains 1 user

  Scenario: we can assert that no message has been sent to a topic
    * the exposed-users topic contains 0 user

  Scenario: we can assert that a json message has been sent on a topic
    When this json message is published on the json-users topic:
      """yml
      id: 1
      name: bob
      """
    Then the json-users topic contains only this json message:
      """yml
      id: 1
      name: bob
      """
    And the json-users topic contains 1 json message

  Scenario Template: we can assert that a json message has been sent on a topic (repeatedly)
    When this json message is published on the json-users topic:
      """yml
      id: 1
      name: bob
      """
    Then if <consume> == true => the json-users topic contains only this json message:
      """yml
      id: 1
      name: bob
      """
    And the json-users topic contains 1 json message
    Examples:
      | consume |
      | false   |
      | true    |
      | false   |
      | true    |
      | true    |

  Scenario: we can assert that a topic will contain a message sent asynchronously
    When after 100ms this user is published on the exposed-users topic:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      """
    Then the exposed-users topic contains 1 user

  Scenario: we can use an avro schema having an array of primitives
    Given this avro schema:
      """yml
      type: record
      name: Stuff
      fields:
        - name: id
          type: int
        - name: change_set
          type:
            type: array
            items:
              type: string
              avro.java.string: String
              default: []
      """
    And that this Stuff is published on the stuffs topic:
      """yml
      id: 1
      change_set:
        - STATUS
        - ITEMS
      """
    Then the stuffs topic contains 1 stuff

  Scenario: we specify that the messages have to be successfully consumed by the listener (without throwing an expection)
    Given that the message counter will success, error then success
    And these json messages are successfully consumed from the json-users-input topic:
      | id | name    |
      | 1  | bob     |
      | 2  | patrick |
      | 3  | carlo   |
    Then we have received 3 messages on the topic json-users-input

  Scenario: we can actively wait for a topic to be fully consumed
    Given that the message counter will success, error then success
    And these json messages are published on the json-users-input topic:
      | id | name    |
      | 1  | bob     |
      | 2  | patrick |

    When the users-group-id group id has fully consumed the json-users-input topic
    Then we have received 2 messages on the topic json-users-input
