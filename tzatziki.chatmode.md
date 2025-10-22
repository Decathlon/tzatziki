---
description: 'Tzatziki specialist.'
tools: ['extensions', 'codebase', 'usages', 'vscodeAPI', 'problems', 'changes', 'testFailure', 'terminalSelection', 'terminalLastCommand', 'openSimpleBrowser', 'fetch', 'findTestFiles', 'searchResults', 'githubRepo', 'runCommands', 'runTasks', 'editFiles', 'runNotebooks', 'search', 'new']
---
Mission
Act as a senior QA engineer and Behavior-Driven Development (BDD) specialist with deep expertise in end-to-end testing using the Tzatziki framework. Your mission is to write high-quality, comprehensive Gherkin tests for the provided user story.

Core Principles
Behavior-Driven: Focus on the business domain and user behavior. All steps (Given, When, Then) must describe the system from an external, "black box" perspective.
Use only steps provided by Tzatziki. Do not invent new step definitions.
For the name of the tables, entities, fields ... use the exact same names as the provided examples.
Privilege yaml formatting

Modularity & Reusability: Use generic, reusable steps. Avoid writing redundant or overly specific step definitions to ensure the test suite is maintainable.

Clear & Concise: Each Scenario title must accurately and concisely reflect the user behavior being tested.

Strict Rules
Language: All Gherkin syntax and comments must be in English only.

Mocking Strategy: Assume that only third-party services are mocked. The Given steps must describe the state of these external mocks and any required test data setup for the scenario.

Scenario Structure:

For a single user story, generate a single .feature file.

Use Scenario Outline with an Examples table to test multiple data combinations for a single behavior. Never write multiple, similar scenarios for these variations.

Case Coverage: Include tests for both passing cases (the "happy path") and failing cases (e.g., invalid input, edge cases). For failing cases, the Then step must explicitly verify the expected error message.

Asynchronous Operations: For asynchronous operations, use the within keyword with an explicit and reasonable timeout (e.g., Then within(5000)...). Never test for the non-existence of an element or a state in an asynchronous step.

Validation: Ensure every Then step contains a clear, verifiable validation of the expected outcome.

Task
Analyze the provided user story and generate the full Gherkin syntax (Feature, Scenario, Given, When, Then, And, But).
---

**Initial Task:**

I need to write an integration test for the following user story: **[Insert User Story here]**

Provide a `Feature` file with one or more `Scenario` that fully covers this user story, strictly following all the rules above. Remember to provide both passing and failing cases if applicable.


---
Feature examples

This file is a merged representation of a subset of the codebase, containing specifically included files, combined into a single document by Repomix.
The content has been processed where empty lines have been removed, security check has been disabled.

<file_summary>
This section contains a summary of this file.

<purpose>
This file contains a packed representation of a subset of the repository's contents that is considered the most important context.
It is designed to be easily consumable by AI systems for analysis, code review,
or other automated processes.
</purpose>

<file_format>
The content is organized as follows:
1. This summary section
2. Repository information
3. Directory structure
4. Repository files (if enabled)
5. Multiple file entries, each consisting of:
  - File path as an attribute
  - Full contents of the file
</file_format>

<usage_guidelines>
- This file should be treated as read-only. Any changes should be made to the
  original repository files, not this packed version.
- When processing this file, use the file path to distinguish
  between different files in the repository.
- Be aware that this file may contain sensitive information. Handle it with
  the same level of security as you would the original repository.
</usage_guidelines>

<notes>
- Some files may have been excluded based on .gitignore rules and Repomix's configuration
- Binary files are not included in this packed representation. Please refer to the Repository Structure section for a complete list of file paths, including binary files
- Only files matching these patterns are included: **/*.feature
- Files matching patterns in .gitignore are excluded
- Files matching default ignore patterns are excluded
- Empty lines have been removed from all files
- Security check has been disabled - content may contain sensitive information
- Files are sorted by Git change count (files with more changes are at the bottom)
</notes>

</file_summary>

<directory_structure>
tzatziki-core/
  src/
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                objects.feature
                scenario-with-background.feature
tzatziki-http/
  src/
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                http.feature
tzatziki-http-mockserver-legacy/
  src/
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                http.feature
tzatziki-logback/
  src/
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                logger.feature
tzatziki-opensearch/
  src/
    test/
      resources/
        features/
          opensearch.feature
tzatziki-spring/
  src/
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                spring.feature
tzatziki-spring-jpa/
  src/
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                spring-jpa.feature
tzatziki-spring-kafka/
  src/
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                kafka.feature
tzatziki-spring-mongodb/
  src/
    test/
      resources/
        features/
          spring-mongo.feature
tzatziki-testng/
  src/
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                scenario-with-background-parallel.feature
</directory_structure>

<files>
This section contains the contents of the repository's files.

<file path="tzatziki-core/src/test/resources/com/decathlon/tzatziki/steps/objects.feature">
Feature: to interact with objects in the context

  Scenario: we can set a variable in the current context and assert it (short java style version)
    Given that something = "test"
    Then something == "test"

  Scenario: we can set a variable in the current context and assert it (short literal style version)
    Given that something is "test"
    Then something is equal to "test"
    And something is equal to "?e te(?:ts|st)"
    Given that something is "tets"
    Then something is equal to "?e te(?:ts|st)"

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
#    Then map.name is equal to "test"
#    And map.attribute is equal to a String "value"
#    And map.object.attribute is equal to an Integer "1"
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
  Scenario: handling bidirectional relationships
    Given that order is an com.decathlon.tzatziki.cyclicgraph.Order:
        """yml
        id: 1
        name: order1
        orderLines:
          - id: 1
            sku: abcdef
            quantity: 42
          - id: 2
            sku: ghijkl
            quantity: 21
        """
    And orderLines references order
    Then order is equal to:
      """yml
        id: 1
        name: order1
        orderLines:
          - id: 1
            sku: abcdef
            quantity: 42
          - id: 2
            sku: ghijkl
            quantity: 21
      """
    # The JsonBackReference annotation on the OrderLine class prevents infinite recursion to happen in this situation
  
  @someTag
  Scenario: we can access the tags in a scenario
    * _scenario.sourceTagNames[0] == "@someTag"


  Scenario Template: we can access the tags in a scenario template
    * _scenario.sourceTagNames[0] == "@<firstTag>"
    * _scenario.sourceTagNames[1][5-] == "<arg>"

    @test1 @arg=value1
    Examples:
      | firstTag | arg    |
      | test1    | value1 |

    @test2 @arg=value2
    Examples:
      | firstTag | arg    |
      | test2    | value2 |

  Scenario Template: we can write to and read from files
    Given that dateAppendedFilePath is:
    """
    <path>-{{{[@now as a formatted date YYYY-MM-dd'T'HH_mm_ss]}}}
    """
    Given that we output in "{{{dateAppendedFilePath}}}":
      """yml
      id: 1
      name: bob
      """
    When bob is "{{{[&dateAppendedFilePath]}}}"
    Then bob is equal to:
      """yml
      id: 1
      name: bob
      """

    Examples:
      | path                    |
      | bob.yaml                |
      | test1/bob.yaml          |
      | /test2/test/../bob.yaml |

  Scenario: we can use file as templates
    Given that userTemplatePath is:
    """
    templates/userTemplate.yaml
    """
    When name is "Alice"
    When alice is:
    """
    {{{[&userTemplatePath]}}}
    """
    Then alice is equal to:
      """yml
      id: 1
      name: Alice
      """
    
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
    Then if bob.name != Bob => bob.id is 3
    And if now before {{{[@2 mins ago]}}} => bob.id is 4
    But if bob.id == 1 && <incrementId> == true => bob.id is 2
    And it is not true that a SkipStepException is thrown when if bob.name == Bob => bob.name == "Toto"
    Then bob.id == <expectedId>
    And bob is equal to:
      """yml
      id: <expectedId>
      name: Bob
      """

    Examples:
      | incrementId | expectedId |
      | true        | 2          |
      | false       | 1          |

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

  Scenario: testing a null field of a table
    Given that users is:
      | id | name |
      | 1  |      |
    Then users contains:
      | id | name    |
      | 1  | ?isNull |
    And users[0].id is equal to 1
    And users[0].name is equal to null

  Scenario: we can compare json objects in table
    * details is a Map:
      """
        {"key":"value"}
      """
    Given that users is:
      | id | detail          |
      | 1  | {"key":"value"} |
    Then users contains:
      | id | detail      |
      | 1  | {{details}} |

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
    Given that _env.TEST = "something"
    Then _env.TEST is equal to "something"

  Scenario: we can access the system properties from the test
    Given that _properties.test = "something"
    Then _properties.test is equal to "something"

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

  Scenario: we can use a variable as a template
    Given that template is:
      """yml
      property: "{{value}}"
      """
    And that value is "test"
    Then template is:
      """yml
      property: "test"
      """
    But if value is "test2"
    Then template is:
      """yml
      property: "test2"
      """

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
      | param     | placeholder             |
      | {{value}} | {{{[_examples.param]}}} |
      | {{value}} | {{value}}               |

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
      """json
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

    Then during 100ms bob is equal to:
      """yml
      id: 1
      user: bob
      """
    And within 150ms bob is equal to null


  Scenario: we can create a null typed object
    Given that user is a User:
      """
      null
      """
    Then user is equal to null

  Scenario: we can expect an exception using guards
    Then an exception MismatchedInputException is thrown when badlyTypedObject is a User:
      """json
      a terribly incorrect json
      """
    And exception.message is equal to "?contains Cannot construct instance of `com.decathlon.tzatziki.User`"

  Scenario: we can expect an unnammed exception using guards
    Then a MismatchedInputException is thrown when badlyTypedObject is a User:
      """json
      a terribly incorrect json
      """
    # default name for the exception is _exception
    And _exception.message is equal to "?contains Cannot construct instance of `com.decathlon.tzatziki.User`"

  Scenario: we can chain multiple guards
    Given that working is "true"
    Then within 50ms it is not true that working is equal to "false"

  Scenario: some additional chain guards
    Given that test is "true"
    Then it is not true that test is equal to "false"
    And it is not true that it is not true that test is equal to "true"
    But it is not true that it is not true that it is not true that test is equal to "false"
    And it is not true that it is not true that it is not true that it is not true that test is equal to "true"
    And it is not true that within 100ms it is not true that during 100ms it is not true that test is equal to "true"

  Scenario Template: some additional conditional chain guards
    Given that if <shouldDoTask> == true => after 100ms taskDone is "true"
    Then if <shouldDoTask> == true => within 150ms taskDone is equal to "true"

    Examples:
      | shouldDoTask |
      | true         |
      | false        |

  Scenario: we can also chain conditional in any order
    Given that ran is "false"
    And that if 1 != 1 => if 1 != 2 => ran is "true"
    Then if 1 != 2 => if 1 != 1 => ran is equal to "true"
    And ran is equal to "false"

  Scenario: concatenate multiple arrays using handlebars helper
    Given that myFirstArray is:
    """
    array:
      - payload: firstItem
      - payload: secondItem
    """
    And that mySecondArray is:
    """
    - payload: thirdItem
    - payload: fourthItem
    """
    And that myThirdArray is:
    """
    - payload: fifthItem
    - payload: sixthItem
    """

    When resultArray is:
    """
    {
      {{#concat [myFirstArray.array] mySecondArray myThirdArray}}
      "myArray": {{this}}
      {{/concat}}
    }
    """

    Then resultArray is equal to:
    """
    {"myArray": [{"payload":"firstItem"},{"payload":"secondItem"},{"payload":"thirdItem"},{"payload":"fourthItem"},{"payload":"fifthItem"},{"payload":"sixthItem"}]}
    """

  Scenario: array to templated-string array with handlebars helper
    Given that rawItems is:
    """
    items:
      - id: item1
        value: value1
      - id: item2
        value: value2
    """

    When that wrappedItems is a List<ListWrapper<java.lang.Object>>:
    """hbs
    {{#foreach [rawItems.items]}}
    - wrapper:
        - {{this}}
    {{/foreach}}
    """

    Then wrappedItems is equal to:
    """
    - wrapper:
        - id: item1
          value: value1
    - wrapper:
        - id: item2
          value: value2
    """

  Scenario: noIndent helper can be used to help increase readability in scenario while allowing handlebars to properly interpret the String
    Given that helloWorld is "Hello World"
    Given that chainedMethodCalls is:
    """
    {{noIndent '{{[

    helloWorld
      .replaceAll(e, 3)
      .replaceAll(l, 1)
      .replaceAll(
        o,
        0
      )

    ]}}'}}
    """
    Then chainedMethodCalls is equal to:
    """
    H3110 W0r1d
    """

  Scenario Template: else guard allows to run a step only if the latest-evaluated condition was false
    Given that condition is "<ifCondition>"
    When if <ifCondition> == true => ran is "if"
    * else ran is "else"
    Then ran is equal to "<expectedRan>"

    Examples:
      | ifCondition | expectedRan |
      | true        | if          |
      | false       | else        |

  Scenario: DataTable can have null value which can be asserted
    Given that unknownPersons is:
      | name |
      |      |
    Then unknownPersons is equal to:
    """
    - name: null
    """
    And unknownPersons is equal to:
      | name |
      |      |

  Scenario: we can call a method without parameters
    Given that aList is a List:
    """
    - hello
    - mr
    """
    When the method size of aList is called
    Then _method_output == 2

  Scenario Template: we can call a method by name providing parameters and assert its return
    Given that aListWrapper is a ListWrapper<String>:
    """
    wrapper:
    - hello
    - bob
    """
    When the method <methodCalled> of aListWrapper is called with parameters:
    """
    <params>
    """
    Then _method_output is equal to:
    """
    <expectedReturn>
    """
    And aListWrapper is equal to:
    """
    <expectedListState>
    """

    Examples:
      | methodCalled | params                     | expectedReturn | expectedListState                |
      | add          | {"element":"mr","index":1} | null           | {"wrapper":["hello","mr","bob"]} |

  Scenario: we can call a method providing parameters by name and assert its exception through guard
    Given that aList is a List:
    """
    - hello
    - bob
    """
    Then an exception java.lang.IndexOutOfBoundsException is thrown when the method get of aList is called with parameter:
    """
    bobby: 2
    """
    And exception.message is equal to:
    """
    Index 2 out of bounds for length 2
    """

  Scenario Template: we can also call methods by parameter order if there is multiple candidates for the given parameter count
    Given that aListWrapper is a ListWrapper<String>:
    """
    wrapper:
    - hello
    - bob
    """
    When the method getOrDefault of aListWrapper is called with parameters:
    """
    bobby: 3
    tommy: <secondParameter>
    """
    Then _method_output is equal to:
    """
    <expectedReturn>
    """
    Examples:
      | secondParameter | expectedReturn |
      | 0               | hello          |
      | fallbackTommy   | fallbackTommy  |

  Scenario: we can call a static method by specifying a class
    When the method read of com.decathlon.tzatziki.utils.Mapper is called with parameters:
    """
    objectToRead: |
      id: 1
      name: bob
    wantedType: com.decathlon.tzatziki.User
    """
    Then _method_output.class is equal to:
    """
    com.decathlon.tzatziki.User
    """
    And _method_output is equal to:
    """
    id: 1
    name: bob
    score: null
    """

  Scenario: we can call a method inline within a variable assignment
    When users is a List<String>:
    """
    - toto
    - bob
    """
    And bobbyVar is "bobby"
    When previousUserAtPosition is "{{{[users.set(1, {{{bobbyVar}}})]}}}"
    Then previousUserAtPosition is equal to "bob"
    And users is equal to:
    """
    - toto
    - bobby
    """
    When previousUserAtPosition is "{{{[users.set(0, stringUser)]}}}"
    Then previousUserAtPosition is equal to "toto"
    And users is equal to:
    """
    - stringUser
    - bobby
    """

  Scenario: we can call a method for a property assignment either on an instance or statically (Mapper)
    When users is a List<String>:
    """
    - toto
    - bob
    """
    And that usersProxy is a Map:
    """
    users: {{{users}}}
    bobIsInBefore: {{{[users.contains(bob)]}}}
    lastRemovedUser: {{{[users.set(1, stringUser)]}}}
    bobIsInAfter: {{{[users.contains(bob)]}}}
    lastAddedUser: {{{[users.get(1)]}}}
    isList: {{{[Mapper.isList({{{users}}})]}}}
    """
    Then usersProxy is equal to:
    """
    users:
    - toto
    - bob
    bobIsInBefore: true
    lastRemovedUser: bob
    bobIsInAfter: false
    lastAddedUser: stringUser
    isList: true
    """
    But users is equal to:
    """
    - toto
    - stringUser
    """

  Scenario: we can use dot-notation to specify nested fields
    Given that yamlNests is a List<Nest>:
    """
    - subNest.bird.name: Titi
    - subNest.subNest:
        subNest.bird.name: Tutu
        bird.name: Tata
    """
    Then yamlNests contains only:
    """
    - subNest:
        bird:
          name: Titi
    - subNest:
        subNest:
          subNest:
            bird:
              name: Tutu
          bird:
            name: Tata
    """
    Given that jsonNests is a List<Nest>:
    """
    [
      {
        "subNest.bird.name": "Titi"
      },
      {
        "subNest.subNest": {
          "subNest.bird.name": "Tutu",
          "bird.name": "Tata"
        }
      }
    ]
    """
    And jsonNests contains only:
    """
    [
      {
        "subNest": {
          "bird": {
            "name": "Titi"
          }
        }
      },
      {
        "subNest": {
          "subNest": {
            "subNest": {
              "bird": {
                "name": "Tutu"
              }
            },
            "bird": {
              "name": "Tata"
            }
          }
        }
      }
    ]
    """

  Scenario: a nested list with dot notation
    Given that listWithNestedList is a List:
    """
    - element.nestedList:
      - element.message: a message
      message: another message
    """
    Then listWithNestedList is equal to:
    """
    - element:
        nestedList:
        - element:
            message: a message
      message: another message
    """

  Scenario: dot notation should only take dot notation for keys (even if the value contains dots and colons)
    Given that object is:
    """
    current_time.timestamp: '2021-08-01T12:30:00.000+02:00'
    """
    Then object is equal to:
    """
    current_time:
      timestamp: '2021-08-01T10:30:00Z'
    """

  Scenario: contains should work even if an expected with a map is matched against a non-map (empty string for eg.)
    Given that aList is a List<Map>:
    """
    - id: 1
      value: ''
    - id: 2
      value.name: toto
    """
    Then aList contains only:
    """
    - id: 2
      value.name: toto
    - id: 1
      value: ''
    """

  Scenario: custom flags can be created in Cucumber runner and used in assertions (note that the isEvenAndInBounds flag is custom and wont be available for you)
    Given that aList is a List<Map>:
    """
    - id: 1
      value: ''
    - id: 2
      value.name: toto
    """
    Then aList contains only:
    """
    - id: 1
      value: ''
    - id: ?isEvenAndInBounds 1 | 2
      value.name: toto
    """

  @ignore @run-manually
  Scenario: an async steps failing should generate an error in the After step
    Given that after 10ms value is equal to "test"
</file>

<file path="tzatziki-core/src/test/resources/com/decathlon/tzatziki/steps/scenario-with-background.feature">
Feature: a feature with a background that we template from the examples in the scenario

  Background:
    Given that map is a Map:
      """yml
      property: {{{[_examples.testValue]}}}
      """
    And if map.property == 4 => map.property is equal to 4

  Scenario Template: test 1 and 2
    Then map.property is equal to <testValue>

    Examples:
      | testValue |
      | 1         |
      | 2         |

  Scenario Template: test 3 and 4
    Then map.property is equal to <testValue>
    And if map.property == 3 => map.property is equal to 3

    Examples:
      | testValue |
      | 3         |
      | 4         |

  Scenario: another scenario that doesn't have examples
    Then map.property is equal to "_examples.testValue"
</file>

<file path="tzatziki-http/src/test/resources/com/decathlon/tzatziki/steps/http.feature">
Feature: to interact with an http service and setup mocks

  Background:
    Given we listen for incoming request on a test-specific socket

  Scenario Outline: we can setup a mock and call it
    Given that calling "<protocol>://backend/hello" will return:
      """yml
      message: Hello world!
      """
    When we call "<protocol>://backend/hello"
    Then we receive:
      """yml
      message: Hello world!
      """
    And "<protocol>://backend/hello" has received a GET
    When if we call "<protocol>://backend/hello"
    Then "<protocol>://backend/hello" has received exactly 2 GETs
    Then "<protocol>://backend/hello" has received at least 1 GET
    Then "<protocol>://backend/hello" has received at most 3 GETs
    Examples:
      | protocol |
      | http     |
      | https    |


  Scenario: we support accent encoding
    Given that calling "http://backend/salut" will return:
      """yml
      message: Salut à tous!
      """
    When we call "http://backend/salut"
    Then we receive:
      """yml
      message: Salut à tous!
      """

  Scenario: we can assert that requests have been received in a given order
    Given that calling "http://backend/hello" will return:
      """yml
      message: Hello world!
      """
    And that posting on "http://backend/hello" will return:
      """yml
      message: Thank you!
      """
    When we call "http://backend/hello"
    And we post on "http://backend/hello":
      """yml
      message: Hello little you!
      """
    And we call "http://backend/hello"

    Then "http://backend/hello" has received in order:
      """yml
      - method: GET
      - method: POST
        body:
          payload:
            message: Hello little you!
      - method: GET
      """

    But it is not true that "http://backend/hello" has received in order:
      """yml
      - method: POST
        body:
          payload:
            message: Hello little you!
      - method: GET
      - method: GET
      """

  Scenario: we can still assert a payload as a list
    Given that posting on "http://backend/hello" will return:
      """yml
      message: Thank you!
      """
    And we post on "http://backend/hello":
      """yml
      - message: Hello little 1!
      - message: Hello little 2!
      - message: Hello little 3!
      """

    Then "http://backend/hello" has received a POST and only and in order:
      """yml
      - message: Hello little 1!
      - message: Hello little 2!
      - message: Hello little 3!
      """

  Scenario: we can assert that a mock is called with a payload
    Given that posting "http://backend/hello" will return a status OK
    When we post on "http://backend/hello":
      """yml
      message: Hello service!
      """
    Then we receive a status OK
    And "http://backend/hello" has received a POST and:
      """yml
      message: Hello service!
      """

  Scenario Template: we can assert that a mock is called with a payload conditionally
    Given that posting "http://backend/hello" will return a status <status>
    When we post on "http://backend/hello":
      """yml
      message: Hello service!
      """
    Then we receive a status <status>
    And if <status> == OK => "http://backend/hello" has received a POST and:
      """yml
      message: Hello service!
      """

    Examples:
      | status    |
      | OK        |
      | FORBIDDEN |

  Scenario: we can setup a mock with query params and call it
    Given that calling "http://backend/hello?name=bob&someParam=true" will return:
      """yml
      message: Hello bob!
      """
    When we call "http://backend/hello?name=bob&someParam=true"
    Then we receive:
      """yml
      message: Hello bob!
      """

  Scenario: we can access the request object to use it in the response
    Given that calling "http://backend/hello?name=.*" will return:
      """yml
      message: Hello \{{request.query.name}}! # handlebars syntax for accessing arrays
      """
    When we call "http://backend/hello?name=bob"
    Then we receive:
      """yml
      message: Hello bob!
      """

  Scenario: we can access the request parameters with a regex to use it in the response
    Given that calling "http://backend/hello?name=(.*)" will return:
      """yml
      message: Hello $1!
      """
    When we call "http://backend/hello?name=bob"
    Then we receive:
      """yml
      message: Hello bob!
      """

  Scenario Template: we can access the request parameters with a regex to use it in the response over a another mock
    Given that calling "http://backend/hello?provider=test&name=(.*)" will return:
      """yml
      message: Hello $1!
      """
    But that if "<name>" == "bob" => calling "http://backend/hello?provider=test&name=.*" will return a status NOT_FOUND_404
    When we call "http://backend/hello?provider=test&name=<name>"
    Then if "<name>" == "bob" => we receive a status NOT_FOUND_404
    And if "<name>" == "lisa" => we receive:
      """yml
      message: Hello <name>!
      """

    Examples:
      | name |
      | bob  |
      | lisa |

  Scenario: we can use an object to define a mock
    Given that "http://backend/hello" is mocked as:
      """yml
      request:
        method: GET
      response:
        status: OK
        headers:
          Content-Type: application/json
        delay: 10
        body:
          payload: |
            {"message":"Bonjour à tous!"}
      """
    When we call "http://backend/hello"
    Then we receive:
      """json
      {"message":"Bonjour à tous!"}
      """

  Scenario: we can explicitly allow for unhandled requests on the wiremock server (default is false)
    Given that calling "http://backend/hello" will return a status OK
    And that we allow unhandled mocked requests
    When we call "http://backend/somethingElse"
    Then we receive a status 404

  Scenario: we can explicitly allow for simple specific unhandled requests on the wiremock server (default is false)
    Given that calling "http://backend/hello" will return a status OK
    And that we allow unhandled mocked requests getting on "http://backend/somethingElse"
    When we call "http://backend/somethingElse"
    Then we receive a status 404

  Scenario: we can explicitly allow for complex specific unhandled requests on the wiremock server (default is false)
    Given that calling "http://backend/hello" will return a status OK
    And that we allow unhandled mocked requests on "http://backend/allowedUnhandled":
    """
    method: POST
    headers:
      some: ?eq header
    body.payload:
      some: ?eq payload
    """
    When we send on "http://backend/allowedUnhandled":
    """
    method: POST
    headers:
      some: header
    body.payload:
      some: payload
    """
    Then we receive a status 404

  Scenario: we can send and assert a complex request
    Given that "http://backend/something" is mocked as:
     """yml
      request:
        method: POST
        headers:
          Authorization: Bearer GeneratedToken
          Content-Type: application/xml; charset=UTF-8
        body:
          payload: |-
            <?xml version="1.0" encoding="utf-8"?>
            <something property="value"/>
      response:
        status: ACCEPTED
      """
    When we post on "http://backend/something" a Request:
      """yml
      headers:
        Authorization: Bearer GeneratedToken
        Content-Type: application/xml; charset=UTF-8
      body:
        payload: |
          <?xml version="1.0" encoding="utf-8"?>
          <something property="value"/>
      """
    Then we receive a status ACCEPTED
    And "http://backend/something" has received a POST and a Request:
      """yml
      headers:
        Authorization: ?eq Bearer GeneratedToken
        Content-Type: application/xml; charset=UTF-8
      body:
        payload: |
          <?xml version="1.0" encoding="utf-8"?>
          <something property="value"/>
      """
    And "http://backend/something" has received a POST and a Request:
      """yml
      headers:
        Authorization: Bearer GeneratedToken
        Content-Type: application/xml; charset=UTF-8
      """
    But if we post on "http://backend/something" a Request:
      """yml
      headers:
        Authorization: Bearer GeneratedToken
        Content-Type: application/xml; charset=UTF-8
      body:
        payload: |
          <?xml version="1.0" encoding="utf-8"?>
          <something property="some other value"/>
      """
    Then we receive a status NOT_FOUND
    * we allow unhandled mocked requests

  Scenario: we can add a pause in the mock
    Given that calling "http://backend/hello" will take 10ms to return a status OK and "Hello you!"
    Then calling "http://backend/hello" returns a status OK and "Hello you!"

  Scenario: we can override a mock
    Given that calling "http://backend/hello" will return a status 404
    But that calling "http://backend/hello" will return a status 200
    When we call "http://backend/hello"
    Then we receive a status 200

  Scenario: we can send a header in a GET request
    Given that calling "http://backend/hello" will return a status 200
    When we send on "http://backend/hello":
      """yml
      method: GET
      headers:
        Some-Token: Some-Value
      """
    Then we receive a status OK_200
    And "http://backend/hello" has received at least:
      """yml
      method: GET
      headers:
        Some-Token: Some-Value
      """

  Scenario: we can mock and assert a Response as a whole
    Given that calling "http://backend/hello" will return a Response:
      """yml
      headers:
        x-api-key: something
      body:
        payload:
          message: some value
      """
    When we call "http://backend/hello"
    Then we receive a Response:
      """yml
      headers:
        x-api-key: something
      body:
        payload:
          message: some value
      """
    And _response.headers.x-api-key == "something"
    And _response.body.payload.message == "some value"

  Scenario: we can define the assertion type in the response assert step
    Given that calling "http://backend/list" will return:
      """yml
      - id: 1
        name: thing 1
        property: test 1
      - id: 2
        name: thing 2
        property: test 2
      - id: 3
        name: thing 3
        property: null
      """
    When we call "http://backend/list"
    Then we receive at least:
      """yml
      - id: 2
      - id: 1
        name: thing 1
      """
    And we receive at least and in order:
      """yml
      - id: 1
        name: thing 1
      - id: 2
      """
    And we receive only:
      """yml
      - id: 1
      - id: 3
      - id: 2
      """
    And we receive only and in order:
      """yml
      - id: 1
      - id: 2
      - id: 3
      """
    And we receive exactly:
      """yml
      - id: 1
        name: thing 1
        property: test 1
      - id: 2
        property: test 2
        name: thing 2
      - id: 3
        name: thing 3
        property: null
      """

  Scenario: we can define the assertion type for the received payload
    Given that posting on "http://backend/users" will return a status CREATED_201
    When we post on "http://backend/users":
      """yml
      id: 1
      name: bob
      """
    And that we receive a status CREATED_201
    Then "http://backend/users" has received a POST and:
      """yml
      name: bob
      """
    And "http://backend/users" has received a POST and at least:
      """yml
      name: bob
      """
    And "http://backend/users" has received a POST and only:
      """yml
      id: 1
      """
    And "http://backend/users" has received a POST and exactly:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can template a value in the mock URL
    Given that putting on "http://backend/test/someValue" will return a status OK_200
    And that value is "someValue"
    When we put on "http://backend/test/someValue":
      """yml
      message: something
      """
    Then "http://backend/test/{{value}}" has received a PUT and:
      """yml
      message: something
      """

  Scenario: we can template a value in the caller URL
    Given that putting on "http://backend/test/someValue" will return a status OK_200
    And that value is "someValue"
    When we put on "http://backend/test/{{value}}":
      """yml
      message: something
      """
    Then "http://backend/test/someValue" has received a PUT and:
      """yml
      message: something
      """

  Scenario: overriding expectations from a previous scenario
    Given that "http://backend/test" is mocked as:
      """yml
      request:
        method: POST
      response:
        status: NOT_ACCEPTABLE
      """
    When we post on "http://backend/test" a String "plop"
    Then we receive a status NOT_ACCEPTABLE

  Scenario: we can send and assert a complex request with a json body given as a yaml
    Given that "http://backend/something" is mocked as:
      """yml
      request:
        method: POST
        body:
          payload:
            items:
              - id: 1
              - id: 2
      response:
        status: ACCEPTED
      """
    When we post on "http://backend/something":
      """yml
      items:
        - id: 1
        - id: 2
      """
    Then we receive a status ACCEPTED

  Scenario: the order of the fields in a mock don't matter if we give a concrete type
    Given that "http://backend/something" is mocked as:
      """yml
      request:
        method: POST
        body:
          type: User
          payload:
            name: bob
            id: 1
      response:
        status: ACCEPTED
      """
    When we post on "http://backend/something":
      """yml
      id: 1
      name: bob
      """
    Then we receive a status ACCEPTED

  Scenario: a mock with a query string
    Given that calling "http://backend/test?test=1" will return "value"
    When we call "http://backend/test?test=1"
    Then we receive "value"

  Scenario: a mock with a query string that we override
    Given that calling "http://backend/test?test=1" will return "value"
    When we call "http://backend/test?test=1"
    Then we receive "value"

  Scenario Template: we can assert properly that a call has been made with headers and query params
    Given that getting on "http://backend/v1/resource?item=123&option=2" will return:
      """yml
      item_id: some-id
      """
    When we send on "http://backend/v1/resource?item=123&option=2":
      """yml
      method: GET
      headers:
        x-api-key: a-valid-api-key
        Authorization: Bearer GeneratedToken
      """
    Then "http://backend/v1/resource<params>" has received:
      """yml
      method: GET
      headers:
        x-api-key: a-valid-api-key
        Authorization: Bearer GeneratedToken
      """
    Examples:
      | params             |
      |                    |
      | .*                 |
      | ?item=12.*         |
      | ?item=123&option=2 |

  Scenario Template: we can override a mock with a lesser match between 2 scenarios
    * if <status> == ACCEPTED => calling "http://backend/test/.*/f" will return a status ACCEPTED
    * if <status> == BAD_GATEWAY => calling "http://backend/test/a/b/c/d/e/f" will return a status BAD_GATEWAY
    When we call "http://backend/test/a/b/c/d/e/f"
    Then we receive a status <status>

    Examples:
      | status      |
      | ACCEPTED    |
      | BAD_GATEWAY |
      | ACCEPTED    |

  Scenario: we can capture a path parameter and replace it with a regex
    Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
      """yml
      item_id: $1
      """
    When we call "http://backend/v1/resource/item/123"
    Then we receive:
      """yml
      item_id: 123
      """
    And "http://backend/v1/resource/item/123" has received a GET
    And "http://backend/v1/resource/item/123" has received:
      """yml
      - method: GET
      """

  Scenario: we can capture a path parameter and template it using the wiremock server request
    Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
      """yml
      item_id: \{{request.pathSegments.6}}
      """
    When we call "http://backend/v1/resource/item/123"
    Then we receive:
      """yml
      item_id: 123
      """

  Scenario: we can capture a path parameter and return a mocked list of responses
    Given that getting on "http://backend/v1/resource/items/(.*)" will return a List:
    """
    \{{#split request.pathSegments.6 ','}}
    - item_id: \{{this}}
    \{{/split}}
    """
    When we call "http://backend/v1/resource/items/1,2,3"
    Then we receive:
      """yml
      - item_id: 1
      - item_id: 2
      - item_id: 3
      """

  Scenario: we can use the body of a post to return a mocked list of responses
    Given that posting on "http://backend/v1/resource/items" will return a List:
      """hbs
      \{{#each (parseJson request.body)}}
      - id: \{{this.id}}
        name: nameOf\{{this.id}}
      \{{/each}}
      """
    When we post on "http://backend/v1/resource/items":
      """yml
      - id: 1
      - id: 2
      - id: 3
      """
    Then we receive:
      """yml
      - id: 1
        name: nameOf1
      - id: 2
        name: nameOf2
      - id: 3
        name: nameOf3
      """

  Scenario: we can make and assert a GET with a payload
    Given that getting on "http://backend/endpoint" will return:
      """yml
      message: \{{lookup (parseJson request.body) 'text'}}
      """
    When we get on "http://backend/endpoint" with:
      """yml
      text: test
      """
    Then we receive:
      """yml
      message: test
      """
    And "http://backend/endpoint" has received a GET and:
      """yml
      text: test
      """

  Scenario: we can make and assert a GET with a templated payload
    Given that getting on "http://backend/endpoint" will return:
      """yml
      message: \{{lookup (parseJson request.body) 'message.text'}}
      """
    And that payload is a Map:
      """yml
      message:
        text: test
      """
    When we get on "http://backend/endpoint" with:
      """
      {{payload}}
      """
    Then we receive:
      """yml
      message: test
      """

  Scenario: we can assert that we received a get on an url with queryParams
    Given that calling "http://backend/endpoint?param=test&user=bob" will return a status OK_200
    When we call "http://backend/endpoint?param=test&user=bob"
    And that we received a status OK_200
    Then "http://backend/endpoint?param=test&user=bob" has received a GET

  Scenario: we can assert that we received a get on an url with queryParams and a capture group
    Given that getting on "http://backend/endpoint/sub?childId=(\d+)&childType=7&type=COUNTRY_STORE" will return a status OK_200 and:
      """yml
      something: woododo
      """
    When we call "http://backend/endpoint/sub?childId=2605&childType=7&type=COUNTRY_STORE"
    And that we received a status OK_200
    Then "http://backend/endpoint/sub?childId=2605&childType=7&type=COUNTRY_STORE" has received a GET

  Scenario: we can wait to assert an interaction
    Given that getting on "http://backend/endpoint" will return a status OK
    When we get on "http://backend/endpoint"
    And that after 20ms we get "http://backend/endpoint"
    Then it is not true that during 50ms "http://backend/endpoint" has received at most 1 GET

  Scenario: we can assert a call within a timeout
    Given that posting on "http://backend/endpoint" will return a status OK
    When we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 2
          zones:
            - id: 3
      """
    Then during 10ms "http://backend/endpoint" has received at most 1 POST

  Scenario: we can assert a some complex stuff on a received payload
    Given that posting on "http://backend/endpoint" will return a status OK
    When we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 1
          zones:
            - id: 1
            - id: 2
        - id: 2
          zones:
            - id: 3
      """
    Then "http://backend/endpoint" has received a POST payload
    And payload.request.body.containers[0].zones.size == 2

  Scenario: we can assert all the posts received
    Given that posting on "http://backend/endpoint" will return a status OK
    When we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 1
          zones:
            - id: 1
            - id: 2
      """
    And we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 2
          zones:
            - id: 3
      """
    Then "http://backend/endpoint" has received 2 POST payloads
    And payloads[0].request.body.containers[0].zones.size == 2
    And payloads[1].request.body.containers[0].zones.size == 1

  Scenario: delete and NO_CONTENT
    Given that deleting on "http://backend/endpoint" will return a status NO_CONTENT_204
    When we delete on "http://backend/endpoint"
    Then we receive a status NO_CONTENT_204

  Scenario: we can assert a status and save the payload inline
    Given that calling "http://backend/endpoint" will return:
      """yml
      key: value
      """
    When we call "http://backend/endpoint"
    Then we receive a status OK_200 and a message
    And message.key is equal to "value"

  Scenario: we can save the payload inline
    Given that calling "http://backend/endpoint" will return:
      """yml
      key: value
      """
    When we call "http://backend/endpoint"
    Then we receive a message
    And message.key is equal to "value"

  Scenario: we can save a typed payload inline
    Given that calling "http://backend/endpoint" will return:
      """yml
      key: value
      """
    When we call "http://backend/endpoint"
    Then we receive a Map message
    And message.size is equal to 1

  Scenario: we can assert a response in one line
    Given that calling "http://backend/endpoint" will return:
      """yml
      key: value
      """
    Then a user calling "http://backend/endpoint" receives:
      """yml
      key: value
      """

  Scenario: we can assert a complex request in one line
    Given that we allow unhandled mocked requests posting on "http://backend/endpointplop"
    And that posting on "http://backend/endpointplop" will return a status NOT_FOUND_404
    And that after 100ms "http://backend/endpointplop" is mocked as:
      """yml
      request:
        method: POST
        body:
          payload: plop
      response:
        status: ACCEPTED_202
      """
    Then within 10000ms a user sending on "http://backend/endpointplop" receives:
      """yml
      request:
        method: POST
        body:
          payload: plop
      response:
        status: ACCEPTED_202
      """

  Scenario Template: calling a url with only a subset of the repeated querystring parameters shouldn't be a match
    * we allow unhandled mocked requests
    Given that calling "http://backend/endpoint?item=1" will return a status CREATED_201
    And that calling "http://backend/endpoint?item=2" will return a status ACCEPTED_202
    And that calling "http://backend/endpoint?item=1&item=2" will return a status OK_200
    When we call "http://backend/endpoint?<params>"
    Then we receive a status <status>

    Examples:
      | params               | status        |
      | item=1               | CREATED_201   |
      | item=2               | ACCEPTED_202  |
      | item=1&item=2        | OK_200        |
      | item=2&item=1        | OK_200        |
      | item=3               | NOT_FOUND_404 |
      | item=1&item=2&item=3 | OK_200        |

  Scenario: repeated query parameters are exposed as an array in templates
    Given that calling "http://backend/collect?item=1&item=2" will return:
      """yml
      items:
        \{{#each request.query.item}}
        - \{{this}}
        \{{/each}}
      """
    When we call "http://backend/collect?item=1&item=2"
    Then we receive:
      """yml
      items:
        - 1
        - 2
      """

  Scenario: later stub overrides earlier stub for same endpoint
    Given that calling "http://backend/hello?name=(.*)" will return:
      """yml
      message: regex $1
      """
    And that calling "http://backend/hello?name=bob" will return:
      """yml
      message: literal
      """
    When we call "http://backend/hello?name=bob"
    Then we receive:
      """yml
      message: literal
      """

  Scenario: The order of items in a list should not be a matching criteria when we give in a payload of a given type (prevent exact String comparison)
    # To specify we don't want the order of an array to have an influence we can either:
    # - specify a body type different from String (JSON comparison)
    Given that "http://backend/endpoint" is mocked as:
      """yml
      request:
        method: POST
        body:
          type: List
          payload:
            - firstItem
            - secondItem
      response:
        status: OK_200
      """
    # - add a Content-Type application/json|xml
    Given that "http://backend/endpoint" is mocked as:
      """yml
      request:
        headers:
          Content-Type: application/json
        method: POST
        body:
          payload:
            - thirdItem
            - fourthItem
      response:
        status: OK_200
      """
    Then a user sending on "http://backend/endpoint" receives:
      """yml
      request:
        method: POST
        body:
          payload:
            - secondItem
            - firstItem
      response:
        status: OK_200
      """
    And a user sending on "http://backend/endpoint" receives:
      """yml
      request:
        method: POST
        body:
          payload:
            - fourthItem
            - thirdItem
      response:
        status: OK_200
      """

    Then "http://backend/endpoint" has received:
      """yml
      method: POST
      body:
        payload:
          - firstItem
          - secondItem
      """
    And "http://backend/endpoint" has received:
      """yml
      method: POST
      body:
        payload:
          - secondItem
          - firstItem
      """

    And "http://backend/endpoint" has received:
      """yml
      method: POST
      body:
        payload:
          - thirdItem
          - fourthItem
      """
    And "http://backend/endpoint" has received:
      """yml
      method: POST
      body:
        payload:
          - fourthItem
          - thirdItem
      """

  Scenario: We want to be able to use template for the count of request against an URI
    Given expectedNumberOfCalls is "2"
    Given that calling "http://backend/endpoint" will return a status OK_200
    When we get "http://backend/endpoint"
    And we get "http://backend/endpoint"
    Then "http://backend/endpoint" has received expectedNumberOfCalls GET

  Scenario: we can access the processing time of the last request we sent
    Given that "http://backend/hello" is mocked as:
      """yml
      request:
        method: GET
      response:
        status: OK
        delay: 10
        body:
          payload: Yo!
      """
    When we call "http://backend/hello"
    Then we receive "Yo!"
    And _response.time is equal to "?ge 10"

  Scenario: test with same bodies should not pass
    And that posting on "http://backend/hello" will return:
      """yaml
      message: Thank you!
      """
    And that we post "http://backend/hello":
      """yaml
      message: Hello little you!
      """
    And that we post "http://backend/hello":
      """yaml
      message: Hello little there!
      """

    Then it is not true that "http://backend/hello" has received only:
      """yaml
      - method: POST
        body:
          payload:
            message: Hello little you!
      - method: POST
        body:
          payload:
            message: Hello little you!
      """

  Scenario: we can assert the interactions on a mock
    Given that calling "http://backend/hello" will return a status INTERNAL_SERVER_ERROR_500
    When we call "http://backend/hello"
    Then the interaction on "http://backend/hello" was:
      """yml
      request:
        method: GET
      response:
        status: INTERNAL_SERVER_ERROR_500
      """

    But if calling "http://backend/hello" will return a status OK_200
    When we call "http://backend/hello"
    And the interactions on "http://backend/hello" were in order:
      """yml
      - response:
          status: INTERNAL_SERVER_ERROR_500
      - response:
          status: OK_200
      """

  Scenario: there shouldn't be any "within" implicit guard in HTTP response assertions
    Given that calling "http://backend/hello" will return a status NOT_FOUND_404 and:
      """
      message: API not found
      """
    Then a user sending on "http://backend/hello" receives:
      """
      request:
        method: GET
      response:
        status: NOT_FOUND_404
        body:
          payload:
            message: API not found
      """

    And that after 500ms calling "http://backend/hello" will return a status OK_200 and:
      """
      message: hello tzatziki
      """

    Then a user sending on "http://backend/hello" receives:
      """
      request:
        method: GET
      response:
        status: NOT_FOUND_404
      """
    And a user calling on "http://backend/hello" returns a status NOT_FOUND_404
    And a user calling on "http://backend/hello" receives a status NOT_FOUND_404 and:
      """
      message: API not found
      """
    And a user calling on "http://backend/hello" receives a Response:
      """
      status: NOT_FOUND_404
      body:
        payload:
          message: API not found
      """

    But within 600ms a user sending on "http://backend/hello" receives:
      """
      request:
        method: GET
      response:
        status: OK_200
        body:
          payload:
            message: hello tzatziki
      """
    And a user calling on "http://backend/hello" returns a status OK_200
    And a user calling on "http://backend/hello" receives a status OK_200 and:
      """
      message: hello tzatziki
      """
    And a user calling on "http://backend/hello" receives a Response:
      """
      status: OK_200
      body:
        payload:
          message: hello tzatziki
      """

  Scenario: there shouldn't be any "within" implicit guard in HTTP wiremock server assertions
    Given that calling "http://backend/hello" will return a status OK_200 and:
      """
      message: hello tzatziki
      """

    When a user calls "http://backend/hello"
    And after 100ms a user sends on "http://backend/hello":
      """
      method: GET
      body:
        payload:
          message: hi
      """

    Then it is not true that "http://backend/hello" has received a GET and:
      """
      message: hi
      """
    And it is not true that "http://backend/hello" has received:
      """
      method: GET
      body:
        payload:
          message: hi
      """
    And it is not true that the interactions on "http://backend/hello" were:
      """
      request:
        method: GET
        body:
          payload:
            message: hi
      response:
        status: OK_200
        body:
          payload:
            message: hello tzatziki
      """

    But within 200ms "http://backend/hello" has received a GET and:
      """
      message: hi
      """
    And "http://backend/hello" has received:
      """
      method: GET
      body:
        payload:
          message: hi
      """
    And the interactions on "http://backend/hello" were:
      """
      request:
        method: GET
        body:
          payload:
            message: hi
      response:
        status: OK_200
        body:
          payload:
            message: hello tzatziki
      """

  Scenario Template: previous test's mocks are properly deleted even if overriding mocks match them with regex
    Given that getting on "http://toto/hello/.*" will return a status 200
    Given if <idx> == 1 => getting on "http://toto/hello/1" will return a status 200
    Then getting on "http://toto/hello/1" returns a status 200

    Examples:
      | idx |
      | 1   |
      | 2   |

  Scenario: if we override an existing mock response, it should take back the priority over any in-between mocks
    Given that posting on "http://services/perform" will return a status FORBIDDEN_403
    Given that "http://services/perform" is mocked as:
      """yaml
      request:
        method: POST
        headers:
          Content-Type: application/json
        body:
          payload:
            service_id: 1
      response:
        status: INTERNAL_SERVER_ERROR_500
        headers:
          Content-Type: application/json
        body:
          payload:
            message: 'Error while performing service'
      """
    Given that posting on "http://services/perform" will return a status BAD_REQUEST_400
    Given that "http://services/perform" is mocked as:
      """yaml
      request:
        method: POST
        headers:
          Content-Type: application/json
        body:
          payload:
            service_id: 1
      response:
        status: OK_200
      """
    When we post on "http://services/perform" a Map:
      """yml
      service_id: 1
      """

    Then we received a status OK_200

  Scenario: within guard working with call_and_assert
    Given that calling on "http://backend/asyncMock" will return a status 404
    And that after 500ms calling on "http://backend/asyncMock" will return a status 200 and:
    """
      message: mocked async
    """
    Then getting on "http://backend/asyncMock" returns a status 404
    But within 10000ms getting on "http://backend/asyncMock" returns a status 200 and:
    """
      message: mocked async
    """

  Scenario Template: the "is mocked as" clause should be able to replace capture groups for json
    Given that "http://backend/hello/(.+)" is mocked as:
      """yaml
      request:
        method: GET
      response:
        status: OK_200
        body:
          payload:
            <beforeBody> hello $1<afterBody>
      """
    When we get on "http://backend/hello/toto"
    Then we received a status OK_200 and:
      """
      <beforeBody> hello toto<afterBody>
      """

    Examples:
      | beforeBody  | afterBody    |
      | message:    |              |
      | - message:  |              |
      | nothing but |              |
      | <greetings> | </greetings> |

  Scenario: Multiple calls over a capture-group-included uri should not have conflict when having concurrent calls
    Given that calling on "http://backend/hello/(.*)" will return:
      """
      hello $1
      """
    When after 50ms we get on "http://backend/hello/toto"
    And after 50ms we get on "http://backend/hello/bob"
    Then within 5000ms the interactions on "http://backend/hello/(.*)" were:
      """
      - response:
          body:
            payload: hello toto
      - response:
          body:
            payload: hello bob
      """

  Scenario: Successive calls to a mocked endpoint can reply different responses
    Given that "http://backend/time" is mocked as:
      """
      response:
        - consumptions: 1
          body:
            payload: morning
        - consumptions: 1
          body:
            payload: noon
        - consumptions: 1
          body:
            payload: afternoon
        - consumptions: 1
          body:
            payload: evening
        - status: NOT_FOUND_404
      """
    Then getting on "http://backend/time" returns:
    """
    morning
    """
    Then getting on "http://backend/time" returns:
    """
    noon
    """
    Then getting on "http://backend/time" returns:
    """
    afternoon
    """
    Then getting on "http://backend/time" returns:
    """
    evening
    """
    Then getting on "http://backend/time" returns a status 404
    Then getting on "http://backend/time" returns a status 404

  Scenario: We can use variables from request regex into response also when using an intermediary object
    Given that response is:
    """
    Hello $1
    """
    And that getting on "http://backend/hello/(.*)" will return:
    """
    {{{response}}}
    """
    When we call "http://backend/hello/toto"
    Then we received:
    """
    Hello toto
    """

  Scenario: if case doesn't match in uri, then it should return NOT_FOUND_404
    Given that we allow unhandled mocked requests
    And that getting on "http://backend/lowercase" will return a status OK_200
    When we call "http://backend/lowercase"
    Then we received a status OK_200
    But when we call "http://backend/LOWERCASE"
    Then we received a status NOT_FOUND_404

  Scenario: XML can be sent through 'we send...' step
    Given that "http://backend/xml" is mocked as:
    """
    request:
      method: POST
      body.payload: '<?xml version="1.0" encoding="utf-8"?><ns:user xmlns:ns="http://www.namespace.com">bob</ns:user>'
    response.status: OK_200
    """
    When we post on "http://backend/xml":
    """
    <?xml version="1.0" encoding="utf-8"?><ns:user xmlns:ns="http://www.namespace.com">bob</ns:user>
    """
    Then we received a status OK_200

  Scenario: Brackets should be handled and escaped properly for HTTP mocks
    Given that getting "http://invalid/regex%5B%5D?re[]toto[]=1" will return a status OK_200
    When we get "http://invalid/regex[]?re[]toto[]=1"
    Then we received a status OK_200

  Scenario Template: Exceed max amount of expectation
    Given we add 1-1 mocks for id endpoint
    Given we add <mocksRange> mocks for id endpoint
    Then getting on "http://backend/1" returns:
    """
    Hello 1
    """
    Examples:
      | mocksRange |
      | 2-150      |
      | 151-250    |

  Scenario: Interactions can also be matched with flags
    Given that posting on "http://backend/simpleApi" will return a status OK_200
    When we post on "http://backend/simpleApi" a Request:
    """
    headers:
      X-Request-ID: '12345'
    """
    And we post on "http://backend/simpleApi"
    Then the interaction on "http://backend/simpleApi" was:
    """
    request:
      method: POST
      headers:
        X-Request-ID: ?notNull
    """
    And the interaction on "http://backend/simpleApi" was only:
    """
    - request:
        method: POST
        headers:
          X-Request-ID: ?notNull
    - request:
        method: POST
        headers:
          X-Request-ID: null
    """

  Scenario Template: we support gzip compression when content-encoding header contains 'gzip'
    Given that we listen for incoming request on a test-specific socket
    When we send on "http://127.0.0.1:{{{[serverSocket.localPort]}}}":
    """yaml
    method: POST
    headers.Content-Encoding: gzip
    body:
      payload: '<rawBody>'
    """
    Then the received body on server socket checksum is equal to <gzipEncodedBodyChecksum>

    Given that we listen for incoming request on a test-specific socket
    When we send on "http://127.0.0.1:{{{[serverSocket.localPort]}}}":
    """yaml
    method: POST
    body:
      payload: '<rawBody>'
    """
    Then it is not true that the received body on server socket checksum is equal to <gzipEncodedBodyChecksum>

    Examples:
      | rawBody               | gzipEncodedBodyChecksum |
      | {"message": "hi"}     | 721742                  |
      | <message>hi</message> | 592077                  |

  @ignore @run-manually
  Scenario Template: Mocks from other tests should be considered as unhandled requests
    * a root logger set to INFO
    Given that if <idx> == 1 => getting on "http://backend/unhandled" will return a status OK_200
    And that if <idx> == 2 => getting on "http://backend/justForHostnameMock" will return a status OK_200
    Then we get on "http://backend/unhandled"

    Examples:
      | idx |
      | 1   |
      | 2   |

  @ignore @run-manually
  Scenario Template: If headers or body doesn't match against allowed unhandled requests, it should fail
    And that we allow unhandled mocked requests on "http://backend/allowedUnhandledRequest":
    """
    method: POST
    headers:
      my-header: ?eq a good value
    body:
      payload:
        my-body:
          field: ?eq a good value
    """
    When we post on "http://backend/allowedUnhandledRequest" a Request:
    """
    <request>
    """

    Examples:
      | request                                                                                         |
      | {"headers":{"my-header":"a bad value"},"body":{"payload":{"my-body":{"field":"a good value"}}}} |
      | {"headers":{"my-header":"a bad value"}}                                                         |
      | {"headers":{"my-header":"a good value"},"body":{"payload":{"my-body":{"field":"a bad value"}}}} |
      | {"body":{"payload":{"my-body":{"field":"a bad value"}}}}                                        |

  Scenario: Requests count assertion should also work for digit
    Given that getting on "http://backend/pipe/([a-z]*)/([0-9]*)/(\d+)" will return a status OK_200 and:
    """
    $1|$2|$3
    """
    When we get on "http://backend/pipe/a/1/2"
    Then we received a status OK_200 and:
    """
    a|1|2
    """
    When we get on "http://backend/pipe/c/3/4"
    Then we received a status OK_200 and:
    """
    c|3|4
    """
    And "http://backend/pipe/[a-b]*/1/\d+" has received 1 GET
    And "http://backend/pipe/.*/\d*/\d+" has received 2 GETs

  Scenario: We can assert the order in which the requests were received
    Given that getting on "http://backend/firstEndpoint" will return a status OK_200
    And that posting on "http://backend/secondEndpoint?aParam=1&anotherParam=2" will return a status OK_200
    And that patching on "http://backend/thirdEndpoint" will return a status OK_200
    When we get on "http://backend/firstEndpoint"
    And that we post on "http://backend/secondEndpoint?aParam=1&anotherParam=2" a Request:
    """
    headers.some-header: some-header-value
    body.payload.message: Hello little you!
    """
    And that we patch on "http://backend/thirdEndpoint"
    Then the recorded interactions were in order:
    """
    - method: GET
      path: http://backend/firstEndpoint
    - method: POST
      path: http://backend/secondEndpoint?aParam=1&anotherParam=2
      headers.some-header: some-header-value
      body:
        payload:
          message: Hello little you!
    - method: PATCH
      path: ?e http://backend/third.*
    """
    And the recorded interactions were:
    """
    - method: POST
      path: http://backend/secondEndpoint?anotherParam=2&aParam=1
      headers.some-header: ?notNull
      body:
        payload:
          message: Hello little you!
    - method: PATCH
      path: ?e http://backend/third.*
    """
    But it is not true that the recorded interactions were:
    """
    - method: POST
      path: http://backend/secondEndpoint?anotherParam=2&aParam=1
      headers.some-header: null
      body:
        payload:
          message: Hello little you!
    - method: PATCH
      path: ?e http://backend/third.*
    """
    And it is not true that recorded interactions were in order:
    """
    - method: POST
      path: http://backend/secondEndpoint?aParam=1&anotherParam=2
      body:
        payload:
          message: Hello little you!
    - method: GET
      path: http://backend/firstEndpoint
    - method: PATCH
      path: ?e http://backend/third.*
    """
    And it is not true that the recorded interactions were:
    """
    - method: POST
      path: http://backend/secondEndpoint?aParam=1&anotherParam=2
      body:
        payload:
          message: Hello BIG you!
    - method: GET
      path: http://backend/firstEndpoint
    - method: PATCH
      path: ?e http://backend/third.*
    """
    And it is not true that the recorded interactions were only:
    """
    - method: GET
      path: http://backend/firstEndpoint
    - method: POST
      path: http://backend/secondEndpoint?aParam=1&anotherParam=2
      body:
        payload:
          message: Hello little you!
    """

  Scenario: Http status codes are extended and not limited to WireMock ones
    Given that getting on "http://backend/tooManyRequest" will return a status TOO_MANY_REQUESTS_429
    Then getting on "http://backend/tooManyRequest" returns a status TOO_MANY_REQUESTS_429


  Scenario: Conflicting pattern are properly handled and last mock is prioritized
    Given that getting on "http://backend/test/S(\d)/path/C(\d)" will return a status TOO_MANY_REQUESTS_429

    And that getting on "http://backend/test/S1/path/C2" will return a status OK_200

    Then getting on "http://backend/test/S1/path/C2" returns a status OK_200

    And "http://backend/test/S1/path/C2" has received a GET

  Scenario: Path parameters are properly handled
    Given that getting on "http://backend/test/S(\d)/path/C(\d)" will return a status OK_200

    Then getting on "http://backend/test/S1/path/C2" returns a status OK_200
    Then getting on "http://backend/test/S2/path/C3" returns a status OK_200

    And "http://backend/test/S2/path/C3" has received a GET
    And "http://backend/test/S1/path/C2" has received a GET

  Scenario: we can use relative url
    Given we set relative url base path to "http://backend"
    Given that calling "http://backend" will return:
      """yml
      message: root path
      """
    When we call "/"
    Then we receive:
      """yml
      message: root path
      """

    Given that calling "http://backend/subpath" will return:
      """yml
      message: subpath
      """
    When we call "/subpath"
    Then we receive:
      """yml
      message: subpath
      """

  Scenario: We can use all types of equality operators when asserting headers
    Given that "http://backend/headers" is mocked as:
      """yml
      request:
        method: GET
        headers:
          exact-match: ?eq expected-value
          regex-match: ?e value-[0-9]+
          contains-match: ?contains contains-this
          not-contains-match: ?doesNotContain without-this
          greater-than: ?gt 100
          greater-equal: ?ge 100
          less-than: ?lt 100
          less-equal: ?le 100
          not-equal1: ?not unexpected-value
          not-equal2: ?ne unexpected-value
          not-equal3: ?!= unexpected-value
          in-list: ?in ['value1', 'value2', 'value3']
          not-in-list: ?notIn ['banned1', 'banned2']
          uuid-value: ?isUUID
          null-header: ?isNull
          not-null-header: ?notNull
          date-before: ?before {{@now}}
          date-after: ?after {{@now}}
        body:
          payload:
            service_id: ?gt 100
      response:
        status: OK_200
      """

    When we send on "http://backend/headers":
      """yml
      method: GET
      headers:
        exact-match: expected-value
        regex-match: value-123
        contains-match: text-contains-this-part
        not-contains-match: text-part
        greater-than: 200
        greater-equal: 100
        less-than: 50
        less-equal: 100
        not-equal1: different-value1
        not-equal2: different-value2
        not-equal3: different-value3
        in-list: value2
        not-in-list: allowed
        uuid-value: 123e4567-e89b-12d3-a456-426614174000
        not-null-header: something
        date-before: 2020-07-02T00:00:00Z
        date-after: 2050-07-02T00:00:00Z
      body:
        payload:
          service_id: 190
      """

    Then we receive a status OK_200

    And "http://backend/headers" has received a get and a Request:
      """yml
      headers:
        exact-match: ?eq expected-value
        regex-match: ?e value-[0-9]+
        contains-match: ?contains contains-this
        not-contains-match: ?doesNotContain without-this
        greater-than: ?gt 100
        greater-equal: ?ge 100
        less-than: ?lt 100
        less-equal: ?le 100
        not-equal1: ?not unexpected-value
        not-equal2: ?ne unexpected-value
        not-equal3: ?!= unexpected-value
        in-list: ?in ['value1', 'value2', 'value3']
        not-in-list: ?notIn ['banned1', 'banned2']
        uuid-value: ?isUUID
        null-header: ?isNull
        not-null-header: ?notNull
        date-before: ?before {{@now}}
        date-after: ?after {{@now}}
      body:
        payload:
          service_id: ?gt 100
      """

  Scenario: Concurrency consumption is handled properly
    Given that "http://backend/time" is mocked as:
      """
      response:
        - consumptions: 1
          body:
            payload: morning
        - consumptions: 1
          body:
            payload: noon
        - consumptions: 1
          body:
            payload: afternoon
        - body:
            payload: evening
      """
    Then getting on "http://backend/time" four times in parallel returns:
    """
    - morning
    - noon
    - afternoon
    - evening
    """

  Scenario Outline: We don't reset mock between tests if needed
    Given that we don't reset mocks between tests
    Given that "http://backend/time" is mocked as:
      """
      response:
        - consumptions: 1
          body:
            payload: id_1
        - consumptions: 1
          body:
            payload: id_2
        - consumptions: 1
          body:
            payload: id_3
      """
    Then getting on "http://backend/time" returns:
      """
      <id>
      """
    Examples:
      | id   |
      | id_1 |
      | id_2 |
      | id_3 |
</file>

<file path="tzatziki-http-mockserver-legacy/src/test/resources/com/decathlon/tzatziki/steps/http.feature">
Feature: to interact with an http service and setup mocks

  Scenario Outline: we can setup a mock and call it
    Given that calling "<protocol>://backend/hello" will return:
      """yml
      message: Hello world!
      """
    When we call "<protocol>://backend/hello"
    Then we receive:
      """yml
      message: Hello world!
      """
    And "<protocol>://backend/hello" has received a GET
    When if we call "<protocol>://backend/hello"
    Then "<protocol>://backend/hello" has received exactly 2 GETs
    Then "<protocol>://backend/hello" has received at least 1 GET
    Then "<protocol>://backend/hello" has received at most 3 GETs
    Examples:
      | protocol |
      | http     |
      | https    |


  Scenario: we support accent encoding
    Given that calling "http://backend/salut" will return:
      """yml
      message: Salut à tous!
      """
    When we call "http://backend/salut"
    Then we receive:
      """yml
      message: Salut à tous!
      """

  Scenario: we can assert that requests have been received in a given order
    Given that calling "http://backend/hello" will return:
      """yml
      message: Hello world!
      """
    And that posting on "http://backend/hello" will return:
      """yml
      message: Thank you!
      """
    When we call "http://backend/hello"
    And we post on "http://backend/hello":
      """yml
      message: Hello little you!
      """
    And we call "http://backend/hello"

    Then "http://backend/hello" has received in order:
      """yml
      - method: GET
      - method: POST
        body:
          payload:
            message: Hello little you!
      - method: GET
      """

    But it is not true that "http://backend/hello" has received in order:
      """yml
      - method: POST
        body:
          payload:
            message: Hello little you!
      - method: GET
      - method: GET
      """

  Scenario: we can still assert a payload as a list
    Given that posting on "http://backend/hello" will return:
      """yml
      message: Thank you!
      """
    And we post on "http://backend/hello":
      """yml
      - message: Hello little 1!
      - message: Hello little 2!
      - message: Hello little 3!
      """

    Then "http://backend/hello" has received a POST and only and in order:
      """yml
      - message: Hello little 1!
      - message: Hello little 2!
      - message: Hello little 3!
      """

  Scenario: we can assert that a mock is called with a payload
    Given that posting "http://backend/hello" will return a status OK
    When we post on "http://backend/hello":
      """yml
      message: Hello service!
      """
    Then we receive a status OK
    And "http://backend/hello" has received a POST and:
      """yml
      message: Hello service!
      """

  Scenario Template: we can assert that a mock is called with a payload conditionally
    Given that posting "http://backend/hello" will return a status <status>
    When we post on "http://backend/hello":
      """yml
      message: Hello service!
      """
    Then we receive a status <status>
    And if <status> == OK => "http://backend/hello" has received a POST and:
      """yml
      message: Hello service!
      """

    Examples:
      | status    |
      | OK        |
      | FORBIDDEN |

  Scenario: we can setup a mock with query params and call it
    Given that calling "http://backend/hello?name=bob&someParam=true" will return:
      """yml
      message: Hello bob!
      """
    When we call "http://backend/hello?name=bob&someParam=true"
    Then we receive:
      """yml
      message: Hello bob!
      """

  Scenario: we can access the request object to use it in the response
    Given that calling "http://backend/hello?name=.*" will return:
      """yml
      message: Hello {{{[_request.queryStringParameterList.0.values.0.value]}}}! # handlebars syntax for accessing arrays
      """
    When we call "http://backend/hello?name=bob"
    Then we receive:
      """yml
      message: Hello bob!
      """

  Scenario: we can access the request parameters with a regex to use it in the response
    Given that calling "http://backend/hello?name=(.*)" will return:
      """yml
      message: Hello $1!
      """
    When we call "http://backend/hello?name=bob"
    Then we receive:
      """yml
      message: Hello bob!
      """

  Scenario Template: we can access the request parameters with a regex to use it in the response over a another mock
    Given that calling "http://backend/hello?provider=test&name=(.*)" will return:
      """yml
      message: Hello $1!
      """
    But that if "<name>" == "bob" => calling "http://backend/hello?provider=test&name=.*" will return a status NOT_FOUND_404
    When we call "http://backend/hello?provider=test&name=<name>"
    Then if "<name>" == "bob" => we receive a status NOT_FOUND_404
    And if "<name>" == "lisa" => we receive:
      """yml
      message: Hello <name>!
      """

    Examples:
      | name |
      | bob  |
      | lisa |

  Scenario: we can use an object to define a mock
    Given that "http://backend/hello" is mocked as:
      """yml
      request:
        method: GET
      response:
        status: OK
        headers:
          Content-Type: application/json
        delay: 10
        body:
          payload: |
            {"message":"Bonjour à tous!"}
      """
    When we call "http://backend/hello"
    Then we receive:
      """json
      {"message":"Bonjour à tous!"}
      """

  Scenario: we can explicitly allow for unhandled requests on the mockserver (default is false)
    Given that calling "http://backend/hello" will return a status OK
    And that we allow unhandled mocked requests
    When we call "http://backend/somethingElse"
    Then we receive a status 404

  Scenario: we can explicitly allow for simple specific unhandled requests on the mockserver (default is false)
    Given that calling "http://backend/hello" will return a status OK
    And that we allow unhandled mocked requests getting on "http://backend/somethingElse"
    When we call "http://backend/somethingElse"
    Then we receive a status 404

  Scenario: we can explicitly allow for complex specific unhandled requests on the mockserver (default is false)
    Given that calling "http://backend/hello" will return a status OK
    And that we allow unhandled mocked requests on "http://backend/allowedUnhandled":
    """
    method: POST
    headers:
      some: ?eq header
    body.payload:
      some: ?eq payload
    """
    When we send on "http://backend/allowedUnhandled":
    """
    method: POST
    headers:
      some: header
    body.payload:
      some: payload
    """
    Then we receive a status 404

  Scenario: we can send and assert a complex request
    Given that "http://backend/something" is mocked as:
     """yml
      request:
        method: POST
        headers:
          Authorization: Bearer GeneratedToken
          Content-Type: application/xml; charset=UTF-8
        body:
          payload: |-
            <?xml version="1.0" encoding="utf-8"?>
            <something property="value"/>
      response:
        status: ACCEPTED
      """
    When we post on "http://backend/something" a Request:
      """yml
      headers:
        Authorization: Bearer GeneratedToken
        Content-Type: application/xml; charset=UTF-8
      body:
        payload: |
          <?xml version="1.0" encoding="utf-8"?>
          <something property="value"/>
      """
    Then we receive a status ACCEPTED
    And "http://backend/something" has received a POST and a Request:
      """yml
      headers:
        Authorization: ?eq Bearer GeneratedToken
        Content-Type: application/xml; charset=UTF-8
      body:
        payload: |
          <?xml version="1.0" encoding="utf-8"?>
          <something property="value"/>
      """
    And "http://backend/something" has received a POST and a Request:
      """yml
      headers:
        Authorization: Bearer GeneratedToken
        Content-Type: application/xml; charset=UTF-8
      """
    But if we post on "http://backend/something" a Request:
      """yml
      headers:
        Authorization: Bearer GeneratedToken
        Content-Type: application/xml; charset=UTF-8
      body:
        payload: |
          <?xml version="1.0" encoding="utf-8"?>
          <something property="some other value"/>
      """
    Then we receive a status NOT_FOUND
    * we allow unhandled mocked requests

  Scenario: we can add a pause in the mock
    Given that calling "http://backend/hello" will take 10ms to return a status OK and "Hello you!"
    Then calling "http://backend/hello" returns a status OK and "Hello you!"

  Scenario: we can override a mock
    Given that calling "http://backend/hello" will return a status 404
    But that calling "http://backend/hello" will return a status 200
    When we call "http://backend/hello"
    Then we receive a status 200

  Scenario: we can send a header in a GET request
    Given that calling "http://backend/hello" will return a status 200
    When we send on "http://backend/hello":
      """yml
      method: GET
      headers:
        Some-Token: Some-Value
      """
    Then we receive a status OK_200
    And "http://backend/hello" has received at least:
      """yml
      method: GET
      headers:
        Some-Token: Some-Value
      """

  Scenario: we can mock and assert a Response as a whole
    Given that calling "http://backend/hello" will return a Response:
      """yml
      headers:
        x-api-key: something
      body:
        payload:
          message: some value
      """
    When we call "http://backend/hello"
    Then we receive a Response:
      """yml
      headers:
        x-api-key: something
      body:
        payload:
          message: some value
      """
    And _response.headers.x-api-key == "something"
    And _response.body.payload.message == "some value"

  Scenario: we can define the assertion type in the response assert step
    Given that calling "http://backend/list" will return:
      """yml
      - id: 1
        name: thing 1
        property: test 1
      - id: 2
        name: thing 2
        property: test 2
      - id: 3
        name: thing 3
        property: null
      """
    When we call "http://backend/list"
    Then we receive at least:
      """yml
      - id: 2
      - id: 1
        name: thing 1
      """
    And we receive at least and in order:
      """yml
      - id: 1
        name: thing 1
      - id: 2
      """
    And we receive only:
      """yml
      - id: 1
      - id: 3
      - id: 2
      """
    And we receive only and in order:
      """yml
      - id: 1
      - id: 2
      - id: 3
      """
    And we receive exactly:
      """yml
      - id: 1
        name: thing 1
        property: test 1
      - id: 2
        property: test 2
        name: thing 2
      - id: 3
        name: thing 3
        property: null
      """

  Scenario: we can define the assertion type for the received payload
    Given that posting on "http://backend/users" will return a status CREATED_201
    When we post on "http://backend/users":
      """yml
      id: 1
      name: bob
      """
    And that we receive a status CREATED_201
    Then "http://backend/users" has received a POST and:
      """yml
      name: bob
      """
    And "http://backend/users" has received a POST and at least:
      """yml
      name: bob
      """
    And "http://backend/users" has received a POST and only:
      """yml
      id: 1
      """
    And "http://backend/users" has received a POST and exactly:
      """yml
      id: 1
      name: bob
      """

  Scenario: we can template a value in the mock URL
    Given that putting on "http://backend/test/someValue" will return a status OK_200
    And that value is "someValue"
    When we put on "http://backend/test/someValue":
      """yml
      message: something
      """
    Then "http://backend/test/{{value}}" has received a PUT and:
      """yml
      message: something
      """

  Scenario: we can template a value in the caller URL
    Given that putting on "http://backend/test/someValue" will return a status OK_200
    And that value is "someValue"
    When we put on "http://backend/test/{{value}}":
      """yml
      message: something
      """
    Then "http://backend/test/someValue" has received a PUT and:
      """yml
      message: something
      """

  Scenario: overriding expectations from a previous scenario
    Given that "http://backend/test" is mocked as:
      """yml
      request:
        method: POST
      response:
        status: NOT_ACCEPTABLE
      """
    When we post on "http://backend/test" a String "plop"
    Then we receive a status NOT_ACCEPTABLE

  Scenario: we can send and assert a complex request with a json body given as a yaml
    Given that "http://backend/something" is mocked as:
      """yml
      request:
        method: POST
        body:
          payload:
            items:
              - id: 1
              - id: 2
      response:
        status: ACCEPTED
      """
    When we post on "http://backend/something":
      """yml
      items:
        - id: 1
        - id: 2
      """
    Then we receive a status ACCEPTED

  Scenario: the order of the fields in a mock don't matter if we give a concrete type
    Given that "http://backend/something" is mocked as:
      """yml
      request:
        method: POST
        body:
          type: User
          payload:
            name: bob
            id: 1
      response:
        status: ACCEPTED
      """
    When we post on "http://backend/something":
      """yml
      id: 1
      name: bob
      """
    Then we receive a status ACCEPTED

  Scenario: a mock with a query string
    Given that calling "http://backend/test?test=1" will return "value"
    When we call "http://backend/test?test=1"
    Then we receive "value"

  Scenario: a mock with a query string that we override
    Given that calling "http://backend/test?test=1" will return "value"
    When we call "http://backend/test?test=1"
    Then we receive "value"

  Scenario Template: we can assert properly that a call has been made with headers and query params
    Given that getting on "http://backend/v1/resource?item=123&option=2" will return:
      """yml
      item_id: some-id
      """
    When we send on "http://backend/v1/resource?item=123&option=2":
      """yml
      method: GET
      headers:
        x-api-key: a-valid-api-key
        Authorization: Bearer GeneratedToken
      """
    Then "http://backend/v1/resource<params>" has received:
      """yml
      method: GET
      headers:
        x-api-key: a-valid-api-key
        Authorization: Bearer GeneratedToken
      """
    Examples:
      | params             |
      |                    |
      | .*                 |
      | ?item=12.*         |
      | ?item=123&option=2 |

  Scenario Template: we can override a mock with a lesser match between 2 scenarios
    * if <status> == ACCEPTED => calling "http://backend/test/.*/f" will return a status ACCEPTED
    * if <status> == BAD_GATEWAY => calling "http://backend/test/a/b/c/d/e/f" will return a status BAD_GATEWAY
    When we call "http://backend/test/a/b/c/d/e/f"
    Then we receive a status <status>

    Examples:
      | status      |
      | ACCEPTED    |
      | BAD_GATEWAY |
      | ACCEPTED    |

  Scenario: we can capture a path parameter and replace it with a regex
    Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
      """yml
      item_id: $1
      """
    When we call "http://backend/v1/resource/item/123"
    Then we receive:
      """yml
      item_id: 123
      """
    And "http://backend/v1/resource/item/123" has received a GET
    And "http://backend/v1/resource/item/123" has received:
      """yml
      - method: GET
      """

  Scenario: we can capture a path parameter and template it using the mockserver request
    Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
      """yml
      item_id: {{{[_request.pathParameterList.0.values.0.value]}}}
      """
    When we call "http://backend/v1/resource/item/123"
    Then we receive:
      """yml
      item_id: 123
      """

  Scenario: we can capture a path parameter and return a mocked list of responses
    Given that getting on "http://backend/v1/resource/items/(.*)" will return a List:
      """hbs
      {{#split _request.pathParameterList.0.values.0.value [,]}}
      - item_id: {{this}}
      {{/split}}
      """
    When we call "http://backend/v1/resource/items/1,2,3"
    Then we receive:
      """yml
      - item_id: 1
      - item_id: 2
      - item_id: 3
      """

  Scenario: we can use the body of a post to return a mocked list of responses
    Given that posting on "http://backend/v1/resource/items" will return a List:
      """hbs
      {{#foreach _request.body}}
      - id: {{this.id}}
        name: nameOf{{this.id}}
      {{/foreach}}
      """
    When we post on "http://backend/v1/resource/items":
      """yml
      - id: 1
      - id: 2
      - id: 3
      """
    Then we receive:
      """yml
      - id: 1
        name: nameOf1
      - id: 2
        name: nameOf2
      - id: 3
        name: nameOf3
      """

  Scenario: we can make and assert a GET with a payload
    Given that getting on "http://backend/endpoint" will return:
      """yml
      message: {{{[_request.body.json.text]}}}
      """
    When we get on "http://backend/endpoint" with:
      """yml
      text: test
      """
    Then we receive:
      """yml
      message: test
      """
    And "http://backend/endpoint" has received a GET and:
      """yml
      text: test
      """

  Scenario: we can make and assert a GET with a templated payload
    Given that getting on "http://backend/endpoint" will return:
      """yml
      message: {{{[_request.body.json.message.text]}}}
      """
    And that payload is a Map:
      """yml
      message:
        text: test
      """
    When we get on "http://backend/endpoint" with:
      """
      {{payload}}
      """
    Then we receive:
      """yml
      message: test
      """

  Scenario: we can assert that we received a get on an url with queryParams
    Given that calling "http://backend/endpoint?param=test&user=bob" will return a status OK_200
    When we call "http://backend/endpoint?param=test&user=bob"
    And that we received a status OK_200
    Then "http://backend/endpoint?param=test&user=bob" has received a GET

  Scenario: we can assert that we received a get on an url with queryParams and a capture group
    Given that getting on "http://backend/endpoint/sub?childId=(\d+)&childType=7&type=COUNTRY_STORE" will return a status OK_200 and:
      """yml
      something: woododo
      """
    When we call "http://backend/endpoint/sub?childId=2605&childType=7&type=COUNTRY_STORE"
    And that we received a status OK_200
    Then "http://backend/endpoint/sub?childId=2605&childType=7&type=COUNTRY_STORE" has received a GET

  Scenario: we can wait to assert an interaction
    Given that getting on "http://backend/endpoint" will return a status OK
    When we get on "http://backend/endpoint"
    And that after 20ms we get "http://backend/endpoint"
    Then it is not true that during 50ms "http://backend/endpoint" has received at most 1 GET

  Scenario: we can assert a call within a timeout
    Given that posting on "http://backend/endpoint" will return a status OK
    When we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 2
          zones:
            - id: 3
      """
    Then during 10ms "http://backend/endpoint" has received at most 1 POST

  Scenario: we can assert a some complex stuff on a received payload
    Given that posting on "http://backend/endpoint" will return a status OK
    When we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 1
          zones:
            - id: 1
            - id: 2
        - id: 2
          zones:
            - id: 3
      """
    Then "http://backend/endpoint" has received a POST payload
    And payload.body.json.containers[0].zones.size == 2

  Scenario: we can assert all the posts received
    Given that posting on "http://backend/endpoint" will return a status OK
    When we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 1
          zones:
            - id: 1
            - id: 2
      """
    And we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 2
          zones:
            - id: 3
      """
    Then "http://backend/endpoint" has received 2 POST payloads
    And payloads[0].body.json.containers[0].zones.size == 2
    And payloads[1].body.json.containers[0].zones.size == 1

  Scenario: delete and NO_CONTENT
    Given that deleting on "http://backend/endpoint" will return a status NO_CONTENT_204
    When we delete on "http://backend/endpoint"
    Then we receive a status NO_CONTENT_204

  Scenario: we can assert a status and save the payload inline
    Given that calling "http://backend/endpoint" will return:
      """yml
      key: value
      """
    When we call "http://backend/endpoint"
    Then we receive a status OK_200 and a message
    And message.key is equal to "value"

  Scenario: we can save the payload inline
    Given that calling "http://backend/endpoint" will return:
      """yml
      key: value
      """
    When we call "http://backend/endpoint"
    Then we receive a message
    And message.key is equal to "value"

  Scenario: we can save a typed payload inline
    Given that calling "http://backend/endpoint" will return:
      """yml
      key: value
      """
    When we call "http://backend/endpoint"
    Then we receive a Map message
    And message.size is equal to 1

  Scenario: we can assert a response in one line
    Given that calling "http://backend/endpoint" will return:
      """yml
      key: value
      """
    Then a user calling "http://backend/endpoint" receives:
      """yml
      key: value
      """

  Scenario: we can assert a complex request in one line
    Given that we allow unhandled mocked requests posting on "http://backend/endpointplop"
    And that posting on "http://backend/endpointplop" will return a status NOT_FOUND_404
    And that after 100ms "http://backend/endpointplop" is mocked as:
      """yml
      request:
        method: POST
        body:
          payload: plop
      response:
        status: ACCEPTED_202
      """
    Then within 10000ms a user sending on "http://backend/endpointplop" receives:
      """yml
      request:
        method: POST
        body:
          payload: plop
      response:
        status: ACCEPTED_202
      """

  Scenario Template: calling a url with only a subset of the repeated querystring parameters shouldn't be a match
    * we allow unhandled mocked requests
    Given that calling "http://backend/endpoint?item=1&item=2" will return a status OK_200
    When we call "http://backend/endpoint?<params>"
    Then we receive a status <status>

    Examples:
      | params               | status        |
      | item=1               | NOT_FOUND_404 |
      | item=1&item=2        | OK_200        |
      | item=2&item=1        | OK_200        |
      | item=3               | NOT_FOUND_404 |
      | item=1&item=2&item=3 | NOT_FOUND_404 |

  Scenario: The order of items in a list should not be a matching criteria when we give in a payload of a given type (prevent exact String comparison)
    # To specify we don't want the order of an array to have an influence we can either:
    # - specify a body type different from String (JSON comparison)
    Given that "http://backend/endpoint" is mocked as:
      """yml
      request:
        method: POST
        body:
          type: List
          payload:
            - firstItem
            - secondItem
      response:
        status: OK_200
      """
    # - add a Content-Type application/json|xml
    Given that "http://backend/endpoint" is mocked as:
      """yml
      request:
        headers:
          Content-Type: application/json
        method: POST
        body:
          payload:
            - thirdItem
            - fourthItem
      response:
        status: OK_200
      """
    Then a user sending on "http://backend/endpoint" receives:
      """yml
      request:
        method: POST
        body:
          payload:
            - secondItem
            - firstItem
      response:
        status: OK_200
      """
    And a user sending on "http://backend/endpoint" receives:
      """yml
      request:
        method: POST
        body:
          payload:
            - fourthItem
            - thirdItem
      response:
        status: OK_200
      """

    Then "http://backend/endpoint" has received:
      """yml
      method: POST
      body:
        payload:
          - firstItem
          - secondItem
      """
    And "http://backend/endpoint" has received:
      """yml
      method: POST
      body:
        payload:
          - secondItem
          - firstItem
      """

    And "http://backend/endpoint" has received:
      """yml
      method: POST
      body:
        payload:
          - thirdItem
          - fourthItem
      """
    And "http://backend/endpoint" has received:
      """yml
      method: POST
      body:
        payload:
          - fourthItem
          - thirdItem
      """

  Scenario: We want to be able to use template for the count of request against an URI
    Given expectedNumberOfCalls is "2"
    Given that calling "http://backend/endpoint" will return a status OK_200
    When we get "http://backend/endpoint"
    And we get "http://backend/endpoint"
    Then "http://backend/endpoint" has received expectedNumberOfCalls GET

  Scenario: we can access the processing time of the last request we sent
    Given that "http://backend/hello" is mocked as:
      """yml
      request:
        method: GET
      response:
        status: OK
        delay: 10
        body:
          payload: Yo!
      """
    When we call "http://backend/hello"
    Then we receive "Yo!"
    And _response.time is equal to "?ge 10"

  Scenario: test with same bodies should not pass
    And that posting on "http://backend/hello" will return:
      """yaml
      message: Thank you!
      """
    And that we post "http://backend/hello":
      """yaml
      message: Hello little you!
      """
    And that we post "http://backend/hello":
      """yaml
      message: Hello little there!
      """

    Then it is not true that "http://backend/hello" has received only:
      """yaml
      - method: POST
        body:
          payload:
            message: Hello little you!
      - method: POST
        body:
          payload:
            message: Hello little you!
      """

  Scenario: we can assert the interactions on a mock
    Given that calling "http://backend/hello" will return a status INTERNAL_SERVER_ERROR_500
    When we call "http://backend/hello"
    Then the interaction on "http://backend/hello" was:
      """yml
      request:
        method: GET
      response:
        status: INTERNAL_SERVER_ERROR_500
      """

    But if calling "http://backend/hello" will return a status OK_200
    When we call "http://backend/hello"
    And the interactions on "http://backend/hello" were in order:
      """yml
      - response:
          status: INTERNAL_SERVER_ERROR_500
      - response:
          status: OK_200
      """

  Scenario: there shouldn't be any "within" implicit guard in HTTP response assertions
    Given that calling "http://backend/hello" will return a status NOT_FOUND_404 and:
      """
      message: API not found
      """
    Then a user sending on "http://backend/hello" receives:
      """
      request:
        method: GET
      response:
        status: NOT_FOUND_404
        body:
          payload:
            message: API not found
      """

    And that after 500ms calling "http://backend/hello" will return a status OK_200 and:
      """
      message: hello tzatziki
      """

    Then a user sending on "http://backend/hello" receives:
      """
      request:
        method: GET
      response:
        status: NOT_FOUND_404
      """
    And a user calling on "http://backend/hello" returns a status NOT_FOUND_404
    And a user calling on "http://backend/hello" receives a status NOT_FOUND_404 and:
      """
      message: API not found
      """
    And a user calling on "http://backend/hello" receives a Response:
      """
      status: NOT_FOUND_404
      body:
        payload:
          message: API not found
      """

    But within 600ms a user sending on "http://backend/hello" receives:
      """
      request:
        method: GET
      response:
        status: OK_200
        body:
          payload:
            message: hello tzatziki
      """
    And a user calling on "http://backend/hello" returns a status OK_200
    And a user calling on "http://backend/hello" receives a status OK_200 and:
      """
      message: hello tzatziki
      """
    And a user calling on "http://backend/hello" receives a Response:
      """
      status: OK_200
      body:
        payload:
          message: hello tzatziki
      """

  Scenario: there shouldn't be any "within" implicit guard in HTTP mockserver assertions
    Given that calling "http://backend/hello" will return a status OK_200 and:
      """
      message: hello tzatziki
      """

    When a user calls "http://backend/hello"
    And after 100ms a user sends on "http://backend/hello":
      """
      method: GET
      body:
        payload:
          message: hi
      """

    Then it is not true that "http://backend/hello" has received a GET and:
      """
      message: hi
      """
    And it is not true that "http://backend/hello" has received:
      """
      method: GET
      body:
        payload:
          message: hi
      """
    And it is not true that the interactions on "http://backend/hello" were:
      """
      request:
        method: GET
        body:
          payload:
            message: hi
      response:
        status: OK_200
        body:
          payload:
            message: hello tzatziki
      """

    But within 200ms "http://backend/hello" has received a GET and:
      """
      message: hi
      """
    And "http://backend/hello" has received:
      """
      method: GET
      body:
        payload:
          message: hi
      """
    And the interactions on "http://backend/hello" were:
      """
      request:
        method: GET
        body:
          payload:
            message: hi
      response:
        status: OK_200
        body:
          payload:
            message: hello tzatziki
      """

  Scenario Template: previous test's mocks are properly deleted even if overriding mocks match them with regex
    Given that getting on "http://toto/hello/.*" will return a status 200
    Given if <idx> == 1 => getting on "http://toto/hello/1" will return a status 200
    Then getting on "http://toto/hello/1" returns a status 200

    Examples:
      | idx |
      | 1   |
      | 2   |

  Scenario: if we override an existing mock response, it should take back the priority over any in-between mocks
    Given that posting on "http://services/perform" will return a status FORBIDDEN_403
    Given that "http://services/perform" is mocked as:
      """yaml
      request:
        method: POST
        headers:
          Content-Type: application/json
        body:
          payload:
            service_id: 1
      response:
        status: INTERNAL_SERVER_ERROR_500
        headers:
          Content-Type: application/json
        body:
          payload:
            message: 'Error while performing service'
      """
    Given that posting on "http://services/perform" will return a status BAD_REQUEST_400
    Given that "http://services/perform" is mocked as:
      """yaml
      request:
        method: POST
        headers:
          Content-Type: application/json
        body:
          payload:
            service_id: 1
      response:
        status: OK_200
      """
    When we post on "http://services/perform" a Map:
      """yml
      service_id: 1
      """

    Then we received a status OK_200

  Scenario: within guard working with call_and_assert
    Given that calling on "http://backend/asyncMock" will return a status 404
    And that after 100ms calling on "http://backend/asyncMock" will return a status 200 and:
    """
      message: mocked async
    """
    Then getting on "http://backend/asyncMock" returns a status 404
    But within 10000ms getting on "http://backend/asyncMock" returns a status 200 and:
    """
      message: mocked async
    """

  Scenario Template: the "is mocked as" clause should be able to replace capture groups for json
    Given that "http://backend/hello/(.+)" is mocked as:
      """yaml
      request:
        method: GET
      response:
        status: OK_200
        body:
          payload:
            <beforeBody> hello $1<afterBody>
      """
    When we get on "http://backend/hello/toto"
    Then we received a status OK_200 and:
      """
      <beforeBody> hello toto<afterBody>
      """

    Examples:
      | beforeBody  | afterBody    |
      | message:    |              |
      | - message:  |              |
      | nothing but |              |
      | <greetings> | </greetings> |

  Scenario: Multiple calls over a capture-group-included uri should not have conflict when having concurrent calls
    Given that calling on "http://backend/hello/(.*)" will return:
      """
      hello $1
      """
    When after 50ms we get on "http://backend/hello/toto"
    And after 50ms we get on "http://backend/hello/bob"
    Then within 5000ms the interactions on "http://backend/hello/(.*)" were:
      """
      - response:
          body:
            payload: hello toto
      - response:
          body:
            payload: hello bob
      """

  Scenario: Successive calls to a mocked endpoint can reply different responses
    Given that "http://backend/time" is mocked as:
      """
      response:
        - consumptions: 1
          body:
            payload: morning
        - consumptions: 1
          body:
            payload: noon
        - consumptions: 1
          body:
            payload: afternoon
        - consumptions: 1
          body:
            payload: evening
        - status: NOT_FOUND_404
      """
    Then getting on "http://backend/time" returns:
    """
    morning
    """
    Then getting on "http://backend/time" returns:
    """
    noon
    """
    Then getting on "http://backend/time" returns:
    """
    afternoon
    """
    Then getting on "http://backend/time" returns:
    """
    evening
    """
    Then getting on "http://backend/time" returns a status 404
    Then getting on "http://backend/time" returns a status 404

  Scenario: Concurrency consumption is handled properly
    Given that "http://backend/time" is mocked as:
      """
      response:
        - consumptions: 1
          body:
            payload: morning
        - consumptions: 1
          body:
            payload: noon
        - consumptions: 1
          body:
            payload: afternoon
        - body:
            payload: evening
      """
    Then getting on "http://backend/time" four times in parallel returns:
    """
    - morning
    - noon
    - afternoon
    - evening
    """

  Scenario: We can use variables from request regex into response also when using an intermediary object
    Given that response is:
    """
    Hello $1
    """
    And that getting on "http://backend/hello/(.*)" will return:
    """
    {{{response}}}
    """
    When we call "http://backend/hello/toto"
    Then we received:
    """
    Hello toto
    """

  Scenario: if case doesn't match in uri, then it should return NOT_FOUND_404
    Given that we allow unhandled mocked requests
    And that getting on "http://backend/lowercase" will return a status OK_200
    When we call "http://backend/lowercase"
    Then we received a status OK_200
    But when we call "http://backend/LOWERCASE"
    Then we received a status NOT_FOUND_404

  Scenario: XML can be sent through 'we send...' step
    Given that "http://backend/xml" is mocked as:
    """
    request:
      method: POST
      body.payload: '<?xml version="1.0" encoding="utf-8"?><ns:user xmlns:ns="http://www.namespace.com">bob</ns:user>'
    response.status: OK_200
    """
    When we post on "http://backend/xml":
    """
    <?xml version="1.0" encoding="utf-8"?><ns:user xmlns:ns="http://www.namespace.com">bob</ns:user>
    """
    Then we received a status OK_200

  Scenario: Brackets should be handled and escaped properly for HTTP mocks
    Given that getting "http://invalid/regex%5B%5D?re[]toto[]=1" will return a status OK_200
    When we get "http://invalid/regex[]?re[]toto[]=1"
    Then we received a status OK_200

  Scenario Template: Exceed max amount of expectation
    Given we add 1-1 mocks for id endpoint
    Given we add <mocksRange> mocks for id endpoint
    Then getting on "http://backend/1" returns:
    """
    Hello 1
    """
    Examples:
      | mocksRange |
      | 2-150      |
      | 151-250    |

  Scenario: Interactions can also be matched with flags
    Given that posting on "http://backend/simpleApi" will return a status OK_200
    When we post on "http://backend/simpleApi" a Request:
    """
    headers:
      X-Request-ID: '12345'
    """
    And we post on "http://backend/simpleApi"
    Then the interaction on "http://backend/simpleApi" was:
    """
    request:
      method: POST
      headers:
        X-Request-ID: ?notNull
    """
    And the interaction on "http://backend/simpleApi" was only:
    """
    - request:
        method: POST
        headers:
          X-Request-ID: ?notNull
    - request:
        method: POST
        headers:
          X-Request-ID: null
    """

  Scenario Template: we support gzip compression when content-encoding header contains 'gzip'
    Given that we listen for incoming request on a test-specific socket
    When we send on "http://127.0.0.1:{{{[serverSocket.localPort]}}}":
    """yaml
    method: POST
    headers.Content-Encoding: gzip
    body:
      payload: '<rawBody>'
    """
    Then the received body on server socket checksum is equal to <gzipEncodedBodyChecksum>

    Given that we listen for incoming request on a test-specific socket
    When we send on "http://127.0.0.1:{{{[serverSocket.localPort]}}}":
    """yaml
    method: POST
    body:
      payload: '<rawBody>'
    """
    Then it is not true that the received body on server socket checksum is equal to <gzipEncodedBodyChecksum>

    Examples:
      | rawBody               | gzipEncodedBodyChecksum |
      | {"message": "hi"}     | 721742                  |
      | <message>hi</message> | 592077                  |

  @ignore @run-manually
  Scenario Template: Mocks from other tests should be considered as unhandled requests
    * a root logger set to INFO
    Given that if <idx> == 1 => getting on "http://backend/unhandled" will return a status OK_200
    And that if <idx> == 2 => getting on "http://backend/justForHostnameMock" will return a status OK_200
    Then we get on "http://backend/unhandled"

    Examples:
      | idx |
      | 1   |
      | 2   |

  @ignore @run-manually
  Scenario Template: If headers or body doesn't match against allowed unhandled requests, it should fail
    And that we allow unhandled mocked requests on "http://backend/allowedUnhandledRequest":
    """
    method: POST
    headers:
      my-header: ?eq a good value
    body:
      payload:
        my-body:
          field: ?eq a good value
    """
    When we post on "http://backend/allowedUnhandledRequest" a Request:
    """
    <request>
    """

    Examples:
      | request                                                                                         |
      | {"headers":{"my-header":"a bad value"},"body":{"payload":{"my-body":{"field":"a good value"}}}} |
      | {"headers":{"my-header":"a bad value"}}                                                         |
      | {"headers":{"my-header":"a good value"},"body":{"payload":{"my-body":{"field":"a bad value"}}}} |
      | {"body":{"payload":{"my-body":{"field":"a bad value"}}}}                                        |

  Scenario: Requests count assertion should also work for digit
    Given that getting on "http://backend/pipe/([a-z]*)/([0-9]*)/(\d+)" will return a status OK_200 and:
    """
    $1|$2|$3
    """
    When we get on "http://backend/pipe/a/1/2"
    Then we received a status OK_200 and:
    """
    a|1|2
    """
    When we get on "http://backend/pipe/c/3/4"
    Then we received a status OK_200 and:
    """
    c|3|4
    """
    And "http://backend/pipe/[a-b]*/1/\d+" has received 1 GET
    And "http://backend/pipe/.*/\d*/\d+" has received 2 GETs

  Scenario: We can assert the order in which the requests were received
    Given that getting on "http://backend/firstEndpoint" will return a status OK_200
    And that posting on "http://backend/secondEndpoint?aParam=1&anotherParam=2" will return a status OK_200
    And that patching on "http://backend/thirdEndpoint" will return a status OK_200
    When we get on "http://backend/firstEndpoint"
    And that we post on "http://backend/secondEndpoint?aParam=1&anotherParam=2" a Request:
    """
    headers.some-header: some-header-value
    body.payload.message: Hello little you!
    """
    And that we patch on "http://backend/thirdEndpoint"
    Then the recorded interactions were in order:
    """
    - method: GET
      path: http://backend/firstEndpoint
    - method: POST
      path: http://backend/secondEndpoint?aParam=1&anotherParam=2
      headers.some-header: some-header-value
      body:
        payload:
          message: Hello little you!
    - method: PATCH
      path: ?e http://backend/third.*
    """
    And the recorded interactions were:
    """
    - method: POST
      path: http://backend/secondEndpoint?anotherParam=2&aParam=1
      headers.some-header: ?notNull
      body:
        payload:
          message: Hello little you!
    - method: PATCH
      path: ?e http://backend/third.*
    """
    But it is not true that the recorded interactions were:
    """
    - method: POST
      path: http://backend/secondEndpoint?anotherParam=2&aParam=1
      headers.some-header: null
      body:
        payload:
          message: Hello little you!
    - method: PATCH
      path: ?e http://backend/third.*
    """
    And it is not true that recorded interactions were in order:
    """
    - method: POST
      path: http://backend/secondEndpoint?aParam=1&anotherParam=2
      body:
        payload:
          message: Hello little you!
    - method: GET
      path: http://backend/firstEndpoint
    - method: PATCH
      path: ?e http://backend/third.*
    """
    And it is not true that the recorded interactions were:
    """
    - method: POST
      path: http://backend/secondEndpoint?aParam=1&anotherParam=2
      body:
        payload:
          message: Hello BIG you!
    - method: GET
      path: http://backend/firstEndpoint
    - method: PATCH
      path: ?e http://backend/third.*
    """
    And it is not true that the recorded interactions were only:
    """
    - method: GET
      path: http://backend/firstEndpoint
    - method: POST
      path: http://backend/secondEndpoint?aParam=1&anotherParam=2
      body:
        payload:
          message: Hello little you!
    """

  Scenario: Http status codes are extended and not limited to MockServer ones
    Given that getting on "http://backend/tooManyRequest" will return a status TOO_MANY_REQUESTS_429
    Then getting on "http://backend/tooManyRequest" returns a status TOO_MANY_REQUESTS_429


  Scenario: Conflicting pattern are properly handled and last mock is prioritized
    Given that getting on "http://backend/test/S(\d)/path/C(\d)" will return a status TOO_MANY_REQUESTS_429

    And that getting on "http://backend/test/S1/path/C2" will return a status OK_200

    Then getting on "http://backend/test/S1/path/C2" returns a status OK_200

    And "http://backend/test/S1/path/C2" has received a GET

  Scenario: Path parameters are properly handled
    Given that getting on "http://backend/test/S(\d)/path/C(\d)" will return a status OK_200

    Then getting on "http://backend/test/S1/path/C2" returns a status OK_200
    Then getting on "http://backend/test/S2/path/C3" returns a status OK_200

    And "http://backend/test/S2/path/C3" has received a GET
    And "http://backend/test/S1/path/C2" has received a GET

  Scenario: we can use relative url
    Given we set relative url base path to "http://backend"
    Given that calling "http://backend" will return:
      """yml
      message: root path
      """
    When we call "/"
    Then we receive:
      """yml
      message: root path
      """

    Given that calling "http://backend/subpath" will return:
      """yml
      message: subpath
      """
    When we call "/subpath"
    Then we receive:
      """yml
      message: subpath
      """
</file>

<file path="tzatziki-logback/src/test/resources/com/decathlon/tzatziki/steps/logger.feature">
Feature: to interact with the logger

  Scenario: we can set the log level to OFF
    Given a root logger set to OFF
    When we log as INFO:
      """
      some log lines
      """
    Then the logs are empty

  Scenario Template: we can assert the content of the logs
    Given a root logger set to <level>
    When something logs as ERROR:
      """
      some log lines that should be there
      """
    Then if <level> == INFO => the logs contain:
      """
      - ?e .* some [^ ]+ lines that should be there
      """
    But if <level> == OFF => the logs are empty

    Examples:
      | level |
      | INFO  |
      | OFF   |

  Scenario: we can assert that the logs do not contain something
    Given a root logger set to INFO
    When we log as INFO:
      """
      some log lines that should be there
      """
    Then it is not true that the logs contain:
      """
      - ?e .* some [^ ]+ lines that should not be there
      """

  Scenario: we can set the log level of a specific class
    Given a com.decathlon.tzatziki.steps logger set to DEBUG
    When we log as DEBUG:
      """
      some lines
      """
    Then the logs contain:
      """
      - ?e .* some lines
      """

  Scenario Template: we can assert the content of the logs (log in JSON)
    Given a root logger set to <level>
    And the logs are formatted in json
    When something logs as ERROR:
      """
      some log lines that should be there
      """
    Then if <level> == INFO => the logs contain:
      """
      - ?e  *.*"message":"some [^ ]+ lines that should be there","logger_name":"com.decathlon.tzatziki.steps.LoggerSteps","thread_name":"main","level":"ERROR".*
      """
    But if <level> == OFF => the logs are empty

    Examples:
      | level |
      | INFO  |
      | OFF   |

  Scenario: we can assert that lines are in a given order
    Given a root logger set to INFO
    When we log as INFO:
      """
      this is the first line
      """

    And we log as INFO:
      """
      this is the second line
      """

    Then the logs contain:
      """
      - ?e .*this is the second line.*
      - ?e .*this is the first line.*
      """

    And it is not true that the logs contain in order:
      """
      - ?e .*this is the second line.*
      - ?e .*this is the first line.*
      """

    And the logs contain in order:
      """
      - ?e .*this is the first line.*
      - ?e .*this is the second line.*
      """
  Scenario: we can assert that lines match multiple times
    Given a root logger set to INFO
    When we log as INFO:
      """
      this is the first line
      """

    And we log as INFO:
      """
      this is the first line
      """

    Then the logs contains at least 1 line equal to "?e .*this is the first line.*"

    And the logs contains exactly 2 lines equal to "?e .*this is the first line.*"

    And the logs contains 2 lines == "?e .*this is the first line.*"

    And it is not true that the logs contains at most 1 line equal to "?e .*this is the first line.*"


  Scenario: there shouldn't be any "within" implicit guard in logger response assertions
    When after 500ms something logs as ERROR:
      """
      some log lines that should be there
      """
    Then it is not true that the logs contain:
      """
      - ?e .* some [^ ]+ lines that should be there
      """
    But within 600ms the logs contain:
      """
      - ?e .* some [^ ]+ lines that should be there
      """
</file>

<file path="tzatziki-opensearch/src/test/resources/features/opensearch.feature">
Feature: Interact with a spring boot application that uses OpenSearch as a persistence layer

  Background:

  Scenario: Define users index and insert a user document
    Given that the users index is:
    """json
    {
      "settings": {
        "number_of_shards": "1",
        "number_of_replicas": "2"
      },
      "mappings": {
        "properties": {
          "firstName": {
            "type": "keyword"
          },
          "lastName": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Given that the users index will contain:
      | _id | firstName | lastName |
      | 1   | Darth     | Vader    |

    Then the users index contains:
      | _id | firstName | lastName |
      | 1   | Darth     | Vader    |

  Scenario: Test index mapping verification
    Given that the products index is:
    """json
    {
      "mappings": {
        "properties": {
          "name": {
            "type": "text"
          },
          "price": {
            "type": "double"
          },
          "category": {
            "type": "keyword"
          },
          "tags": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Then the products index mapping is:
    """json
    {
      "properties": {
        "name": {
          "type": "text"
        },
        "price": {
          "type": "double"
        },
        "category": {
          "type": "keyword"
        },
        "tags": {
          "type": "keyword"
        }
      }
    }
    """

  Scenario: Insert multiple documents with custom IDs
    Given that the orders index is:
    """json
    {
      "settings": {
        "number_of_shards": "1"
      },
      "mappings": {
        "properties": {
          "orderId": {
            "type": "keyword"
          },
          "amount": {
            "type": "double"
          },
          "status": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Given that the orders index will contain:
      | _id    | orderId | amount | status    |
      | order1 | ORD001  | 99.99  | completed |
      | order2 | ORD002  | 149.50 | pending   |
      | order3 | ORD003  | 75.25  | cancelled |

    Then the orders index contains:
      | orderId | amount | status    |
      | ORD001  | 99.99  | completed |
      | ORD002  | 149.50 | pending   |
      | ORD003  | 75.25  | cancelled |

  Scenario: Test different comparison modes
    Given that the inventory index is:
    """json
    {
      "mappings": {
        "properties": {
          "productId": {
            "type": "keyword"
          },
          "quantity": {
            "type": "integer"
          }
        }
      }
    }
    """
    Given that the inventory index will contain:
      | _id | productId | quantity |
      | 1   | PROD001   | 50       |
      | 2   | PROD002   | 25       |
      | 3   | PROD003   | 100      |

    Then the inventory index contains exactly:
      | productId | quantity |
      | PROD001   | 50       |
      | PROD002   | 25       |
      | PROD003   | 100      |

    Then the inventory index still contains:
      | productId | quantity |
      | PROD001   | 50       |
      | PROD002   | 25       |

  Scenario: Test nested objects and complex data types
    Given that the articles index is:
    """json
    {
      "mappings": {
        "properties": {
          "title": {
            "type": "text"
          },
          "author": {
            "properties": {
              "name": {
                "type": "keyword"
              },
              "email": {
                "type": "keyword"
              }
            }
          },
          "publishDate": {
            "type": "date"
          },
          "tags": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Given that the articles index will contain:
      | _id | title                    | author                                      | publishDate | tags                    |
      | 1   | Introduction to OpenSearch | {"name":"John Doe","email":"john@test.com"} | 2024-01-15  | ["search","tutorial"]   |

    Then the articles index contains:
      | title                    | author                                      | publishDate | tags                    |
      | Introduction to OpenSearch | {"name":"John Doe","email":"john@test.com"} | 2024-01-15  | ["search","tutorial"]   |

  Scenario: Test empty index
    Given that the empty_test index is:
    """json
    {
      "mappings": {
        "properties": {
          "field1": {
            "type": "keyword"
          }
        }
      }
    }
    """
    Then the empty_test index contains exactly:
      | field1 |
</file>

<file path="tzatziki-spring/src/test/resources/com/decathlon/tzatziki/steps/spring.feature">
Feature: to interact with a spring boot service

  Background:
    * we clear all the caches
    * we clear the nameOfTheCache cache

  Scenario: we can query a spring service
    When we call "/hello"
    Then we receive "Hello world!"

  Scenario: we can manage the cache
    Given the cache nameOfTheCache will contain:
      """yml
      key:
        - field_a: value_a
          field_b: value_b
      """

    Then the cache nameOfTheCache contains:
      """yml
      key:
        - field_a: value_a
      """

    Then the cache nameOfTheCache contains exactly:
      """yml
      key:
        - field_a: value_a
          field_b: value_b
      """

    And it is not true that the cache nameOfTheCache contains exactly:
      """yml
      key:
        - field_a: value_a
      """

    And it is not true that the cache nameOfTheCache contains:
      """yml
      key1:
        - field_a: value_a
      key:
        - field_a: value_b
      """

  Scenario Template: we can mock a real url
    Given that calling "http://backend/greeting" will return "Hello from another backend"
    Then calling "<endpoint>" returns "Hello from another backend"
    But if we disable the HttpInterceptor
    Then calling "<endpoint>" returns a status 500

    Examples:
      | endpoint                                 |
      | /rest-template-remote-hello              |
      | /rest-template-builder-remote-hello      |
      | /rest-template-from-builder-remote-hello |
      | /web-client-remote-hello                 |
      | /web-client-builder-remote-hello         |
      | /web-client-from-builder-remote-hello    |

  Scenario: we can still reach the internet
    When we call "http://www.google.com"
    Then we receive a status 200
    But if calling "http://www.google.com" will return a status FORBIDDEN_403
    Then calling "http://www.google.com" returns a status FORBIDDEN_403

  Scenario: we should use Spring Context's mapper PropertyNamingStrategy by default (snake_case)
    Then it is not true that a JsonMappingException is thrown when myPojo is a NonSnakeCasePojo:
    """
    non_snake_case_field: hello
    """

  Scenario: we can get an application context bean through "_application" ObjectSteps' context variable
    Given that helloController is a HelloController "{{{[_application.getBean({{{HelloController}}})]}}}"
    And that helloResponse is "{{{[helloController.hello()]}}}"
    Then helloResponse.body is equal to "Hello world!"

  Scenario: we start an infinite task if clear thread pool executor is enabled
    Given the thread pool executor is cleaned between test runs
    And that we start an infinite task

  Scenario: then the infinite task has been cancelled
    Then infinite task has been shutdown

  Scenario: we start an infinite task if clear thread pool executor is disabled
    Given the thread pool executor is not cleaned between test runs
    And that we start an infinite task

  Scenario: then the infinite task has not been cancelled
    Then it is not true that infinite task has been shutdown
</file>

<file path="tzatziki-spring-jpa/src/test/resources/com/decathlon/tzatziki/steps/spring-jpa.feature">
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


  Scenario: we can manipulate tables from different schemas and jdbc/jpa repositories at the same time
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
    Given that the visibility table will contain:
      | name    |
      | private |
      | public  |
    And that the groups table will contain:
      | name   | visibility.id |
      | admins | 1             |
      | guests | 2             |
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
      | id | firstName | lastName | group.id | group.name | group.visibility.name |
      | 1  | Chuck     | Norris   | 1        | admins     | private               |
      | 2  | Uma       | Thurman  | 2        | guests     | public                |
      | 3  | Jackie    | Chan     | 2        | guests     | public                |
    
  Scenario: all schemas are cleared before each scenario

    Then the books table contains nothing

    Then the products table contains nothing
</file>

<file path="tzatziki-spring-kafka/src/test/resources/com/decathlon/tzatziki/steps/kafka.feature">
Feature: to interact with a spring boot service having a connection to a kafka queue

  Background:
    * a com.decathlon logger set to DEBUG
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

  Scenario: we can push a json message in a kafka topic where multiple listeners expect a simple payload on the same method
    When this json message is consumed from the json-users-input topic:
      """yml
      id: 1
      name: bob
      """
    Then we have received 1 message on the topic json-users-input

    When this json message is consumed from the json-users-input-2 topic:
      """yml
      id: 1
      name: jack
      """
    Then we have received 1 message on the topic json-users-input-2

  Scenario: we can push a json message with key in a kafka topic
    When this json message is consumed from the json-users-with-key topic:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key: a-key
      """
    Then we have received 1 message on the topic json-users-with-key
    And the logs contain:
      """yml
      - "?e .*received user with messageKey a-key"
      """

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

  Scenario: we can push a message with a key in a kafka topic 1
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

  Scenario: we can push a message with a key in a kafka topic 2
    Given this avro schema:
      """yml
      type: record
      name: user
      fields:
        - name: id
          type: ["null", "int"]
          default: null
        - name: name
          type: ["null", "string"]
          default: null
      """
    When these users are consumed from the users-with-key topic:
      """yml
      headers:
        uuid: some-id
      value: null
      key: a-key
      """
    Then we have received 1 messages on the topic users-with-key
    And the logs contain:
      """yml
      - "?e .*received user with messageKey a-key on users-with-key-0@0: \\{\"id\": null, \"name\": null}"
      """

  Scenario: we can push a message with an avro key in a kafka topic
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
    And this avro schema:
      """yml
      type: record
      name: user_key
      fields:
        - name: a_key
          type: string
      """
    When these users with key user_key are consumed from the users-with-avro-key topic:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key:
        a_key: a-value
      """
    Then we have received 1 messages on the topic users-with-avro-key
    And the logs contain:
      """yml
      - "?e .*received user with messageKey \\{\"a_key\": \"a-value\"} on users-with-avro-key-0@0: \\{\"id\": 1, \"name\": \"bob\"}"
      """

  Scenario: we can push a null message with an avro key in a kafka topic
    Given this avro schema:
      """yml
      type: record
      name: user
      fields:
        - name: id
          type: ["null", "int"]
          default: null
        - name: name
          type: ["null", "string"]
          default: null
      """
    And this avro schema:
      """yml
      type: record
      name: user_key
      fields:
        - name: a_key
          type: string
      """
    When these users with key user_key are consumed from the users-with-avro-key topic:
      """yml
      headers:
        uuid: some-id
      value: null
      key:
        a_key: a-value
      """
    Then we have received 1 messages on the topic users-with-avro-key
    And the logs contain:
      """yml
      - "?e .*received user with messageKey \\{\"a_key\": \"a-value\"} on users-with-avro-key-0@0: \\{\"id\": null, \"name\": null}"
      """

  Scenario Template: replaying a topic should only be replaying the messages received in this test
    When this user is consumed from the users topic:
      """yml
      id: 3
      name: tom
      """
    Then we have received 1 message on the topic users

    And if we empty the logs
    And that we replay the topic users from <from> with a <method>

    Then within 10000ms we have received 2 messages on the topic users
    But if <method> == listener => the logs contain:
      """yml
      - "?e .*received user: \\{\"id\": 3, \"name\": \"tom\"}"
      """
    But if <method> == consumer => the logs contain:
      """yml
      - "?e .*received user on users-0@0: \\{\"id\": 3, \"name\": \"tom\"}"
      """
    # these messages are from the previous test and shouldn't leak
    But it is not true that the logs contain:
      """yml
      - "?e .*received user on users-\\d@\\d+: \\{\"id\": 1, \"name\": \"bob\"}"
      - "?e .*received user on users-\\d@\\d+: \\{\"id\": 2, \"name\": \"lisa\"}"
      """

    Examples:
      | from          | method   |
      | the beginning | consumer |
      | offset 0      | consumer |
      | the beginning | listener |
      | offset 0      | listener |

  Scenario Outline: we can set the offset of a given group-id on a given topic
    When these users are consumed from the users topic:
      """yml
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 3
        name: tom
      """
    Then we have received 3 messages on the topic users

    But if the current offset of <group-id> on the topic users is 1
    And if <group-id> == users-group-id-replay => we resume replaying the topic users

    Then within 10000ms we have received 5 messages on the topic users
    Examples:
      | group-id              |
      | users-group-id        |
      | users-group-id-replay |

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

  Scenario: we can use an avro schema having arrays (with a default value null) of nested records set
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
            - 'null'
            - type: array
              items:
                name: User
                type: record
                fields:
                  - name: id
                    type: int
                  - name: name
                    type: string
      """
    And this Group are consumed from the group-with-users topic:
      """yml
      - id: 1
        name: minions
        users:
          - id: 1
            name: bob
      """
    Then we have received 1 message on the topic group-with-users

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
    # Still we can assert the value only
    And from the beginning the exposed-users topic contains this user:
      """yml
      id: 1
      name: bob
      """
    And the exposed-users topic contains 1 user

  Scenario: we can assert that no message has been sent to a topic
    * the exposed-users topic contains 0 user

  Scenario: we can assert that a json message has been sent on a topic
    When this json message is published on the json-users topic:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key: a-key
      """
    Then the json-users topic contains only this json message:
      """yml
      headers:
        uuid: some-id
      value:
        id: 1
        name: bob
      key: a-key
      """
    # Still we can assert the value only
    And from the beginning the json-users topic contains only this json message:
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

  Scenario: there shouldn't be any "within" implicit guard in Kafka assertions
    Given that this json message is published on the json-users-input topic:
      | id | name |
      | 1  | bob  |

    And that after 300ms this json message is published on the json-users-input topic:
      | id | name    |
      | 2  | patrick |

    Then the json-users-input topic contains 1 json message

    But within 500ms the json-users-input topic contains 2 json messages

  @ignore
  Scenario: we wait for a poll to occur on a specific topic
    When the json-users-input topic was just polled

  Scenario: we can publish with a templated value in the topic name
    Given that topicId is "123"
    And that topicName is "template-topic-{{topicId}}"
    When this user is published on the {{topicName}} topic:
      | id | name |
      | 1  | bob  |
    Then the template-topic-123 topic contains 1 user

  Scenario: we can check with a templated value in the topic name
    Given that myTopicName is "template-topic-2"
    When this json message is published on the template-topic-2 topic:
      """yml
      headers:
        uuid: one-uuid
      value:
        id: 1
        name: bob
      key: a-key
      """
    Then the {{myTopicName}} topic contains this json message:
      """yml
      headers:
        uuid: one-uuid
      value:
        id: 1
        name: bob
      key: a-key
      """

  Scenario: we can push an avro message in a kafka template topic where a listener expect a simple payload
    Given that topicName is "users"
    When this user is consumed from the {{topicName}} topic:
      """yml
      id: 1
      name: bob
      """
    Then we have received 1 message on the topic users

  Scenario Template: we can assert that a message has been sent on a template topic (repeatedly)
    Given that topicName is "exposed-users-topic"
    When this user is published on the {{topicName}} topic:
      """yml
      id: 1
      name: bob
      """
    Then if <consume> == true => the {{topicName}} topic contains only this user:
      """yml
      id: 1
      name: bob
      """
    And the exposed-users-wrong-topic topic contains 0 user
    And the {{topicName}} topic contains 1 message

    Examples:
      | consume |
      | false   |
      | true    |
      | false   |
      | true    |
      | true    |

  Scenario Outline: we can set the offset of a given group-id on a given template topic named
    Given that topicName is "users"
    When these users are consumed from the users topic:
      """yml
      - id: 1
        name: bob
      - id: 2
        name: lisa
      - id: 3
        name: tom
      """
    Then we have received 3 messages on the topic users

    But if the current offset of <group-id> on the topic {{topicName}} is 1
    And if <group-id> == users-group-id-replay => we resume replaying the topic users

    Then within 10000ms we have received 5 messages on the topic users
    Examples:
      | group-id              |
      | users-group-id        |
      | users-group-id-replay |
</file>

<file path="tzatziki-spring-mongodb/src/test/resources/features/spring-mongo.feature">
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

    Scenario: The database is cleaned
      Given that the users document will contain only:
        | id | firstName | lastName |
        | 1  | Darth     | Vader    |
      Then the users document contains:
        | id | firstName | lastName |
        | 1  | Darth     | Vader    |
      When we clean the database
      Then the users document contains nothing
</file>

<file path="tzatziki-testng/src/test/resources/com/decathlon/tzatziki/steps/scenario-with-background-parallel.feature">
Feature: a feature with a background that we template from the examples in the scenario

  Background:
    * if 1 == {{{[_examples.testValue]}}} => map.property is 1
    * if 2 == {{{[_examples.testValue]}}} => map.property is 2
    * if 3 == {{{[_examples.testValue]}}} => map.property is 3
    * if 4 == {{{[_examples.testValue]}}} => map.property is 4
    * if 5 == {{{[_examples.testValue]}}} => map.property is 5
    * if 6 == {{{[_examples.testValue]}}} => map.property is 6
    * if 7 == {{{[_examples.testValue]}}} => map.property is 7
    * if 8 == {{{[_examples.testValue]}}} => map.property is 8
    * if 9 == {{{[_examples.testValue]}}} => map.property is 9

  Scenario Template: our examples are stable between threads while running in parallel
    Then map.property is equal to <testValue>

    Examples:
      | testValue |
      | 1         |
      | 2         |
      | 3         |
      | 4         |
      | 5         |
      | 6         |
      | 7         |
      | 8         |
      | 9         |
</file>

</files>
