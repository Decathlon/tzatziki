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
    Then we receive:
      """yml
      message: Hello world!
      """
     Then "<protocol>://backend/hello" has received exactly 2 GETs
     Then "<protocol>://backend/hello" has received at least 1 GET
     Then "<protocol>://backend/hello" has received at most 3 GETs
    Examples:
      | protocol |
      | http     |
      | https    |

  Scenario: Successive calls to a mocked endpoint can reply different responses

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
        headers:
          Content-Type: application/json; charset=UTF-8
        body:
            payload: evening
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
    Then we receive a Response:
      """yml
        status: ACCEPTED
        headers:
          Content-Type: application/json; charset=UTF-8
        body:
            payload: evening
      """

    And the interactions on "http://backend/something" were:
      """
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
        headers:
          Content-Type: application/json; charset=UTF-8
        body:
            payload: evening
      """

    Given that "http://backend/time?param=2" is mocked as:
      """yml
      request:
        method: POST
        body:
          payload:
            items:
              - id: 1
              - id: 2
      response:
        - consumptions: 1
          headers:
            Content-Type: application/json
          delay: 10
          body:
            payload: morning
        - consumptions: 1
          body:
            payload: noon
        - consumptions: 1
          body:
            payload: afternoon
        - consumptions: 2
          body:
            payload: evening
        - status: NOT_FOUND_404
      """

    When we post on "http://backend/time?param=2":
      """yml
      items:
        - id: 1
        - id: 2
      """
    Then we receive:
      """
      morning
      """

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
      message: Hello {w{request.query.name}w}!
      """
    When we call "http://backend/hello?name=bob"
    Then we receive:
      """yml
      message: Hello bob!
      """

  Scenario: we can access the request parameters with a regex to use it in the response
    Given that calling "http://backend/hello?name=(.*)" will return:
      """yml
      message: Hello {w{request.query.name}w}!
      """
    When we call "http://backend/hello?name=bob"
    Then we receive:
      """yml
      message: Hello bob!
      """

  Scenario Template: we can access the request parameters with a regex to use it in the response over a another mock
    Given that calling "http://backend/hello?provider=test&name=(.*)" will return:
      """yml
      message: Hello {w{request.query.name}w}!
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

  Scenario: we can explicitly allow for unhandled requests on the wiremock (default is false)
    Given that calling "http://backend/hello" will return a status OK
    And that we allow unhandled mocked requests
    When we call "http://backend/somethingElse"
    Then we receive a status 404

  @ignore
  Scenario: we can explicitly allow for simple specific unhandled requests on the mockserver (default is false)
    Given that calling "http://backend/hello" will return a status OK
    And that we allow unhandled mocked requests getting on "http://backend/somethingElse"
    When we call "http://backend/somethingElse"
    Then we receive a status 404

  @ignore
  Scenario: we can explicitly allow for complex specific unhandled requests on the mockserver (default is false)
    Given that calling "http://backend/hello" will return a status OK
    And that we allow unhandled mocked requests on "http://backend/allowedUnhandled":
    """
    method: POST
    headers:
      some: header
    body.payload:
      some: payload
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