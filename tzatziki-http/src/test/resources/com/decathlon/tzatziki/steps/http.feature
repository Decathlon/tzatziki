Feature: to interact with an http service and setup mocks

  Scenario: we can setup a mock and call it
    Given that calling "http://backend/hello" will return:
      """yml
      message: Hello world!
      """
    When we call "http://backend/hello"
    Then we receive:
      """yml
      message: Hello world!
      """
    And "http://backend/hello" has received a GET
    When if we call "http://backend/hello"
    Then "http://backend/hello" has received exactly 2 GETs
    Then "http://backend/hello" has received at least 1 GET
    Then "http://backend/hello" has received at most 3 GETs

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
      message: Hello {{request.queryStringParameterList.0.values.0.value}}! # handlebars syntax for accessing arrays
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
        Authorization: Bearer GeneratedToken
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
    And response.headers.x-api-key == "something"
    And response.body.payload.message == "some value"

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

  Scenario: expectations with and without body redefined in the same scenario
    Given that "http://backend/test" is mocked as:
      """yml
      request:
        method: POST
      response:
        status: NOT_ACCEPTABLE
      """
    And that "http://backend/test" is mocked as:
      """yml
      request:
        method: POST
        body:
          payload: plop
      response:
        status: OK
      """

    When we post on "http://backend/test"
    Then we receive a status NOT_ACCEPTABLE

    But if we post on "http://backend/test" a String "plop"
    Then we receive a status OK

    And if "http://backend/test" is mocked as:
      """yml
      request:
        method: POST
      response:
        status: ACCEPTED
      """
    When we post on "http://backend/test"
    Then we receive a status ACCEPTED

    But if we post on "http://backend/test" a String "plop"
    Then we still receive a status OK

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
      item_id: {{request.pathParameterList.0.values.0.value}}
      """
    When we call "http://backend/v1/resource/item/123"
    Then we receive:
      """yml
      item_id: 123
      """

  Scenario: we can capture a path parameter and return a mocked list of responses
    Given that getting on "http://backend/v1/resource/items/(.*)" will return a List:
      """hbs
      {{#split request.pathParameterList.0.values.0.value [,]}}
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
      {{#foreach request.body}}
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
      message: {{{[request.body.json.text]}}}
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
      message: {{{[request.body.json.message.text]}}}
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
    And "http://backend/endpoint" has received a GET and:
      """yml
      {"message": {
        "text": "test"
       }}
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
    And that we get "http://backend/endpoint" 20ms later
    Then it is not true that "http://backend/endpoint" has received at most 1 GET within 50ms

  Scenario: we can assert a call within a timeout
    Given that posting on "http://backend/endpoint" will return a status OK
    When we post on "http://backend/endpoint":
      """yml
      containers:
        - id: 2
          zones:
            - id: 3
      """
    Then "http://backend/endpoint" has received at most 1 POST within 10ms

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
    Given that after 100ms "http://backend/endpoint" is mocked as:
      """yml
      request:
        method: POST
        body:
          payload: plop
      response:
        status: ACCEPTED_202
      """
    Then a user sending on "http://backend/endpoint" receives:
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
    # - add a Content-Type application/json|xml (with charset specified since in our case HTTP call library adds it)
    Given that "http://backend/endpoint" is mocked as:
      """yml
      request:
        headers:
          Content-Type: application/json; charset=UTF-8
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
    And response.time is equal to "?ge 10"

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
