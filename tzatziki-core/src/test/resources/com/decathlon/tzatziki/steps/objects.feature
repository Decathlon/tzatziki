Feature: to interact with objects in the context

  Scenario: we can set a variable in the current context and assert it (short java style version)
    Given that something = "test"
    Then something == "test"

  Scenario: we can set a variable in the current context and assert it (short literal style version)
    Given that something is "test"
    Then something is equal to "test"
    And something is equal to "?e te.*"

  Scenario: we can set a variable in the current context and assert it (long version)
    Given that something is:
      """
      test
      """
    Then something is equal to:
      """
      test
      """

  Scenario: we can assert a typed attribute of a typed object
    Given that map is a Map:
      """yml
      name: test
      attribute: value
      object:
        name: my super object
        attribute: 1
      parameters:
        - value1
        - value2
      blob: | #this will keep the breaks
        some super long
        text that had to be put
        on different lines
      """
    Then map.name is equal to "test"
    And map.attribute is equal to a String "value"
    And map.object.attribute is equal to an Integer "1"
    And map.threshold is equal to 12
    And map.isEmpty is equal to false
    And map.parameters[1] is equal to "value2"
    And map.parameters.1 is equal to "value2"
    And if value is "{{map.parameters.1}}"
    Then value is equal to "value2"
    # regex matching by prefixing the expected content with ?e
    And map.object is equal to:
      """yml
      name: ?e my super .*
      attribute: ?e [0-9]
      """
    # ignore whitespaces by prefixing the expected content with ?w
    And map.blob is equal to "?w some super long text that had to be put on different lines"
    And map.blob is equal to:
      """
      ?w
      some
      super
      long
      text
      that
      had
      to
      be
      put
      on
      different
      lines
      """

    And map.object contains at least:
      """yml
      attribute: 1
      """

    And map.parameters contains at least "value1"
    And map.parameters contains only:
      """yml
      - value1
      - value2
      """
    And map.parameters.toString contains exactly "[value1, value2]"
    And map.parameters contains:
      """yml
      - value1
      - value2
      """

  Scenario: using a contains on a typed object will not fail on default fields
    Given that bob is:
      """yml
      id: 1
      name: Bob
      """
    Then bob contains a User:
      """yml
      name: Bob
      """

  Scenario: list of maps, this should fail
    When that list is a List:
      """yml
      - store_stock: "100"
        item: "767657"
        ecommerce_stock: "0"
        security_stock: "0"
        real_stock: "100"
        store: "2"
      - store_stock: "100"
        item: "400000"
        ecommerce_stock: "0"
        security_stock: "20"
        real_stock: "80"
        store: "1"
      - store_stock: "100"
        item: "767657"
        ecommerce_stock: "0"
        security_stock: "0"
        real_stock: "100"
        store: "3"
      - store_stock: "100"
        item: "767657"
        ecommerce_stock: "0"
        security_stock: "20"
        real_stock: "80"
        store: "1"
      """
    Then it is not true that list contains only:
      """yml
      - store: "1"
        item: "767657"
        security_stock: "12"
      - store: "1"
        item: "400000"
        security_stock: "13"
      - store: "2"
        item: "767657"
        security_stock: "14"
      - store: "3"
        item: "767657"
        security_stock: "0"
      """

  Scenario: assert of fields using flags
    Given that user is a Map:
      """yml
      id: 1
      name: Bob
      uuid: c8eb85bc-c7fc-4586-9f91-c14e7c9d473e
      age: 20
      created: {{{[@10 mins ago]}}}
      """
    Then user.age == "?eq 20"
    Then user.age == "?== 20"
    Then user.age == "?gt 19"
    Then user.age == "?> 19"
    And user.age == "?ge 20"
    And user.age == "?>= 20"
    And user.age == "?lt 21"
    And user.age == "?< 21"
    And user.age == "?le 20"
    And user.age == "?<= 20"
    And user.age == "?not 0"
    And user.age == "?!= 0"
    And user.name == "?not null"
    And user.created == "?before {{@now}}"
    And user.created == "?after {{{[@20 mins ago]}}}"
    And user.name == "?base64 Qm9i"
    And user.uuid == "?isUUID"
    And user contains:
      """yml
      uuid: ?isUUID
      name: ?e B.*
      """

  Scenario: we can set the time in our tests
    Given that the current time is the first Sunday of November 2020 at midnight
    Then now is equal to "2020-11-01T00:00:00Z"

  Scenario: all fields of a dot map are evaluated
    Given that users is:
      | id | name | created              |
      | 1  | Bob  | {{{[@10 mins ago]}}} |
    Then users[0].created == "{{{[@10 mins ago]}}}"
    And users[0] is equal to:
      """yml
      id: 1
      name: Bob
      created: {{{[@10 mins ago]}}}
      """

  Scenario: assert of deep nested lists
    Given that deepNested is a List:
      """yml
      - id:
          shipping_id: ABC
          container_id: XYZ
        tracking_status: PICKED
        picked_items:
          picked_items:
          - sku_code: "767657"
            picked_quantity: 1
            rfids:
            - "3039606203C7F24000053621"
          - sku_code: "2357060"
            picked_quantity: 3
            rfids:
            - "3039606203C7F24000053622"
            - "3039606203C7F24000053623"
            - "3039606203C7F24000053624"
      """
    Then deepNested contains only:
      """yml
      - id:
          shipping_id: ABC
          container_id: XYZ
        tracking_status: PICKED
        picked_items:
          picked_items:
          - sku_code: "767657"
            picked_quantity: 1
            rfids:
            - "3039606203C7F24000053621"
          - sku_code: "2357060"
            picked_quantity: 3
            rfids:
            - "3039606203C7F24000053622"
            - "3039606203C7F24000053623"
            - "3039606203C7F24000053624"
      """

  Scenario Template: we can access the tags in a scenario
    * scenario.sourceTagNames[0] == "@<firstTag>"
    * scenario.sourceTagNames[1][5-] == "<arg>"

    @test1 @arg=value1
    Examples:
      | firstTag | arg    |
      | test1    | value1 |

    @test2 @arg=value2
    Examples:
      | firstTag | arg    |
      | test2    | value2 |

  Scenario Template: we can write to and read from files
    Given that we output in "<path>":
      """yml
      id: 1
      name: bob
      """
    When bob is "{{{[&<path>]}}}"
    Then bob is equal to:
      """yml
      id: 1
      name: bob
      """

    Examples:
      | path                    |
      | bob.yaml                |
      | test1/bob.yaml         |
      | /test2/test/../bob.yaml |


  Scenario: we cannot write a file outside the resource folder of the build
    * it is not true that we output in "../../bob.yaml":
      """yml
      id: 1
      name: bob
      """

  Scenario: parse negative integer values in tables
    Given that users is a List<User>:
      | id | name | score |
      | 1  | bob  | -42   |
    Then users[0] is equal to a User:
      """yml
      id: 1
      name: bob
      score: -42
      """

  Scenario Template: we can ignore a step based on a predicate
    Given that bob is a Map:
      """yml
      id: 1
      name: Bob
      """
    And if bob.name != Bob => bob.id == 2
    And if now before {{{[@2 mins ago]}}} => bob.id == 2
    Then if bob != null && bob.id != 2 => bob.id == 1
    Then if bob != null && <flag> == on => bob.id == 1
    Then if <flag> == on => bob.name == "Bob"
    Then if bob != null => bob.id == 1

    Examples:
      | flag |
      | on   |
      | off  |

  Scenario: a step can be green because it failed
    Given that bob is a User:
    """yml
    id: 1
    name: bob
    """
    Then it is not true that bob.id == 2

  Scenario: testing a null field
    Given that bob is a User:
    """yml
    id: 1
    name: bob
    """
    Then bob contains:
    """yml
    score: ?isNull
    """

  Scenario: we can compare a serialized Java Array String that starts with a [ but is actually not a Json document without breaking the Mapper
    Given that content is a Map:
      """yml
      message: "[ConstraintViolationImpl{interpolatedMessage='size must be between 1 and 2147483647', messageTemplate='{javax.validation.constraints.Size.message}'}]"
      """
    Then content is equal to:
      """yml
      message: "[ConstraintViolationImpl{interpolatedMessage='size must be between 1 and 2147483647', messageTemplate='{javax.validation.constraints.Size.message}'}]"
      """
    And content is equal to:
      """yml
      message: ?e .*constraints\.Size\.message.*
      """

  Scenario: we can access the ENVs from the test
    # see com.decathlon.tzatziki.utils.Env to see how we can set an environment variable at runtime
    Given that env.TEST = "something"
    Then env.TEST is equal to "something"

  Scenario: we can access the system properties from the test
    Given that properties.test = "something"
    Then properties.test is equal to "something"

  Scenario: we can test that a value is one of a list of values
    Given that object is a Map:
      """yml
      property: value1
      """
    Then object.property == "?in [value1, value2]"
    Then object.property == "?notIn [value3, value4]"
    And object contains:
      """yml
      property: ?in [value1, value2]
      """
    And object contains:
      """yml
      property: ?notIn [value3, value4]
      """

  Scenario: we can test that a value can be parsed as a given type
    Given that object is a Map:
      """yml
      date: 2021-05-29T00:00:00Z
      notAdate: value1
      age: 23
      distance: 2.3
      """
    Then object.date == "?is Instant"
    And object contains:
      """yml
      date: ?is java.time.Instant
      """
    And it is not true that object.notAdate == "?is Instant"
    And it is not true that object.date == "?is Date"
    And object.age == "?is Integer"
    And object.distance == "?is Number"
    And it is not true that object.distance == "?is Boolean"

  Scenario: we can test that value contains another one, or not
    Given that object is a Map:
      """yml
      first: some really long sentence that contains the word bird, you know ...
      second: some really long sentence that doesn't contain the famous word, you know ...
      """
    Then object.first == "?contains bird"
    And object.first == "?e .*bird.*"
    And object.second == "?doesNotContain bird"

  Scenario: we can define a variable while templating it
    Given that object is a Map:
      """yml
      property: "{{{[id: randomUUID.get]}}}"
      time: "{{{[created_at: @now]}}}"
      """
    Then id == "?isUUID"
    And created_at == "{{@now}}"

  Scenario Template: we can use the conditional helpers in handlebar
    Given that object is a Map:
      """hbs
      {{#lt <test>}}
      is: true
      {{else}}
      is: false
      {{/lt}}
      """
    Then object.is is equal to "<result>"

    Examples:
      | test | result |
      | 0 5  | true   |
      | 10 5 | false  |

  Scenario: we can use the math helper to do math stuff in the tests
    Given that object is a Map:
      """yml
      propertyA: 1
      """
    Then object.propertyA is "{{math object.propertyA '+' 1}}"
    When object.propertyA == "2"

  Scenario: we can compare more in the guard
    When object is a Map:
      """yml
      propertyA: 1
      """
    Then if 3 == 3 => object.propertyA == 1


  Scenario: comparing list orders with null values
    Given that list is a List:
      """yml
      [null, 2, null]
      """
    Then list contains in order:
      """yml
      [null, 2, null]
      """
    But it is not true that list contains in order:
      """yml
      [2, null, null]
      """

  Scenario: we can assert that something is true within a given time
    Given that after 100ms bob is:
      """yml
      id: 1
      user: bob
      """
    Then within 200ms bob is equal to:
      """yml
      id: 1
      user: bob
      """

  Scenario Template: we can template recursively from scenario examples
    Given that value is "some value"
    And that templated is:
      """
      <placeholder>
      """
    Then templated is equal to:
      """
      some value
      """
    Examples:
      | param     | placeholder            |
      | {{value}} | {{{[examples.param]}}} |
      | {{value}} | {{value}}              |

  Scenario: we can set an attribute on a map
    Given that bob is a Map:
      """yml
      id: 1
      """
    And that bob.name is "bob"
    And that bob.attributes is a List:
      """yml
      - name: test
      - age: 12
      """
    Then bob.name is equal to "bob"
    And bob.attributes[1].age is equal to 12

    But if bob.attributes[1].age is 15
    Then bob.attributes[1].age is equal to 15

  Scenario: we can set an attribute on an object
    Given that user is a User:
      """yml
      id: 1
      name: bob
      """
    Then user.name is equal to "bob"
    But if user.name is "lisa"
    Then user.name is equal to "lisa"

  Scenario: we can set an attribute on an object in a list
    Given that users is a List<User>:
      """yml
      - id: 1
        name: bob
      - id: 2
        name: lisa
      """
    Then users[0].name is equal to "bob"
    But when users[0].name is "tom"
    Then users[0].name is equal to "tom"

  Scenario: we can lazily create a nested map
    Given that map.prop1.prop2 is "test"
    Then map is equal to:
      """yml
      prop1:
        prop2: test
      """

  Scenario: we can call a method with parameter
    Given that users is a List<User>:
      """yml
      []
      """
    And that users.add is called with a User:
      """yml
      id: 1
      name: bob
      """
    Then users[0].name is equal to "bob"

  Scenario: we can safely template something that doesn't exist
    When someVariable is "{{{[something.that.is.definitely.not.there]}}}"
    Then someVariable is equal to "something.that.is.definitely.not.there"

  Scenario: we can assert that something is true during a given period
    Given that bob is:
      """yml
      id: 1
      user: bob
      """
    But that after 200ms bob is null

    Then during 150ms bob is equal to:
      """yml
      id: 1
      user: bob
      """
    And within 100ms bob is equal to null


  Scenario: we can create a null typed object
    Given that user is a User:
    """
    null
    """
    Then user is equal to null

  Scenario: we can expect an exception using guards
    Then a com.fasterxml.jackson.databind.exc.MismatchedInputException is thrown when badlyTypedObject is a User:
    """json
    a terribly incorrect json
    """
