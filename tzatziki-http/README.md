Tzatziki HTTP Library
======

## Description

This module provides steps to query and/or mock http services.

## Get started with this module

You need to add this dependency to your project:

```xml

<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-http</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

Make sure that you have the bootstrap class described in the [Core README](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-core#get-started-with-this-module)

### Queries

This library wraps [RestAssured](https://rest-assured.io/) in a Gherkin syntax, so that:

```java
given().get("http://backend/hello")
    .then()
    .assertThat()
    .body(equalTo("Hello world!"));
```

becomes:

```gherkin
When we get "http://backend/hello"
Then we receive "Hello world!"
```

The status of a response can be asserted individualy with:
```gherkin
Then we receive a status OK
```

or together with the body:
```gherkin
Then we receive a status OK and:
  """
  message: Hello user!
  """
```

All the status can be expressed by their snake case name, their code or both: `OK`, `200` or `OK_200`

All the http verbs `call|get|head|post|put|patch|delete|trace` can be used without body and `call|get|post|put|patch` with a body.

*Note: `call` is an accepted alias for `get` ... because it makes nicer sentences ...*

Headers can be specified by using a more detailed format:
```gherkin
When we send on "http://backend/hello":
  """
  method: POST
  headers:
    Some-Token: Some-Value
  body:
    payload:
      message: some value  
"""
```

*OPTION requests are only supported using the detailed format.*

*Once again all those urls and inputs will be templated and can be provided in Yaml or Json.*

If you need to send a plain text payload, remember to use the `|` or `>` for your yaml payload:
```gherkin
# again an alternative format
When we post on "http://backend/hello" a Request:
  """
  headers:
    Content-Type: application/xml
  body:
    payload: | 
      <?xml version="1.0" encoding="UTF-8"?>
      <message>Hello world!</message>
  """
```

Finally, if you need to retry a call until it returns a specific value, you can do so with:
```gherkin
Then a user calling "http://backend/endpoint" receives:
  """
  key: value
  """
```

This will wait and retry following what is described in the [timeout and retry delay](#Timeout-and-retry-delay) section.

### OAuth2 Client Credentials Authentication

This library provides built-in support for OAuth2 client credentials flow authentication. This allows you to set up authenticated API calls in your tests.

#### Setting up OAuth2 Authentication

Use the `that the user "<user>" is authenticated with:` step to configure OAuth2 client credentials. This will automatically fetch the access token from the specified token URL and associate it with a user:

```gherkin
Background:
  Given that the user "my-service" is authenticated with:
    """yml
    client_id: my-client-id
    client_secret: secret123
    token_url: "http://auth-server/oauth/token"
    """
```

The docstring accepts the following YAML keys:
- `client_id` — the OAuth2 client ID
- `client_secret` — the OAuth2 client secret
- `token_url` — the OAuth2 token endpoint URL

:warning: Warning: This feature is only for mocked oauth2 servers, as for now we support only a way to provide the clientSecret in plain text. Do not use it with a real oauth2 server if you don't want to expose your secret in your tests.

This step will:
1. Make a POST request to the token URL with `grant_type=client_credentials`
2. Parse the `access_token` from the JSON response
3. Add the `Authorization: Bearer <token>` header for the specified user

#### Making Authenticated HTTP Calls

Once authentication is set up, you can make authenticated HTTP calls using the existing user-based syntax:

```gherkin
# Simple GET request
When my-service calls "http://backend/api/resource"

# POST with body
When my-service posts on "http://backend/api/users":
  """json
  {
    "name": "John Doe"
  }
  """

# Assert response with authentication
Then my-service calling "http://backend/api/status" receives a status OK_200

# Assert response with body
Then my-service calling "http://backend/api/data" receives a status OK_200 and:
  """json
  {
    "result": "success"
  }
  """
```

The authenticated calls will automatically include the `Authorization: Bearer <token>` header.

#### Multiple Authenticated Clients

You can set up multiple OAuth2 clients for different services:

```gherkin
Background:
  Given that the user "service-a" is authenticated with:
    """yml
    client_id: client-a
    client_secret: secret-a
    token_url: "http://auth/token"
    """
  And that the user "service-b" is authenticated with:
    """yml
    client_id: client-b
    client_secret: secret-b
    token_url: "http://auth/token"
    """

Scenario: Different services access different APIs
  When service-a calls "http://backend/api/a"
  Then we receive a status OK_200
  
  When service-b calls "http://backend/api/b"
  Then we receive a status OK_200
```

#### Testing with Mocked OAuth2 Server

In tests, you can mock the OAuth2 token endpoint:

```gherkin
Background:
  Given that posting on "http://auth-server/oauth/token" will return:
    """json
    {
      "access_token": "test-token-12345",
      "token_type": "Bearer",
      "expires_in": 3600
    }
    """
  And that the user "test-client" is authenticated with:
    """yml
    client_id: test-client-id
    client_secret: test-secret
    token_url: "http://auth-server/oauth/token"
    """

Scenario: Make authenticated call to protected API
  Given that calling "http://backend/api/protected" will return:
    """json
    {"message": "Hello authenticated user!"}
    """
  When test-client calls "http://backend/api/protected"
  Then we receive:
    """json
    {"message": "Hello authenticated user!"}
    """
```

But if you want to test your API's authorization, mocking just the token endpoint will not suffice. You will have to use a real oauth2 server. You can use the [mock-oauth2-server](https://github.com/navikt/mock-oauth2-server) for that.


### Mocking and interactions

Internally, WireMock is used for defining mocks and asserting interactions.

#### Define mocks

You can define a mock using a simple step:
```gherkin
Given that posting on "http://backend/users" will return a status CREATED_201
Given that putting on "http://backend/test/someValue" will return a status OK_200

# wildchar on any resource and returning a status
Given that calling "http://backend/.*" will return a status BAD_GATEWAY

# payload  
Given that calling "http://backend/hello" will return:
  """
  message: Hello world!
  """
  
# status and payload  
Given that calling "http://backend/hello" will return a status FORBIDDEN_403 and:
  """
  error_message: run fools!
  """

# introduce a delay
Given that calling "http://backend/hello" will take 10ms to return a status OK and "Hello you!"
```

or with a more detailed step and model:
```gherkin
Given that "http://backend/something" is mocked as:
  """
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
```

By default, the body of a request will match against a mock if the request body contains at least all the fields and array elements of the mock.
Note that you can use keywords "only" to specify a body which will only be matched if there is no extra field / array element.
A step further, you can also use "only and in order" or "exactly" if you want to also match the order of the array elements.
```gherkin
Given that "http://backend/something" is mocked as only and in order:
"""
  request:
    method: POST
    headers:
      # to match as JSON
      Content-Type: application/json
    body:
      payload:
        myOrderedArray:
          - a
          - b
          - c
  response:
    status: ACCEPTED
"""
```
or
```gherkin
Given that "http://backend/something" is mocked as only:
"""
  request:
    method: POST
    headers:
      # to match as JSON
      Content-Type: application/json
    body:
      payload:
        nonOrderedArray:
          - c
          - a
          - b
  response:
    status: ACCEPTED
"""
```

##### Query parameter matching

Query parameters are matched per key. The matching rules are:

- Single value param (e.g. ?name=bob): the value is treated as a regular expression (WireMock matching), so you can use literals (bob), wildcards (.*), or capture groups ( (.*) ). The pattern must match the whole value.
- Repeated param keys (e.g. ?item=1&item=2): all specified values are required, order does not matter and extra values in the incoming request are tolerated. This is implemented with WireMock's including(...) matcher.
- Order of query parameters is never significant (either between different keys or between repeated values of the same key).
- Additional query parameters (keys not declared in the mock) do NOT prevent a match unless their presence changes the body/headers logic you assert elsewhere.
- When a parameter is repeated (item=1&item=2) and you access it in a templated response via Handlebars, it is exposed as an array: {{request.query.item}}.
- If you mix literal and regex mocks for the same endpoint (e.g. ?name=bob and ?name=(.*)), the most recently defined stub still follows WireMock's priority rules (later definition overrides earlier if equally specific).

Examples:
```gherkin
# Multi-value: requires both 1 and 2 (order independent), allows extra values like 3
Given that calling "http://backend/endpoint?item=1&item=2" will return a status OK_200
# These also match the same mock:
When we call "http://backend/endpoint?item=2&item=1"      # order swap
When we call "http://backend/endpoint?item=1&item=2&item=3" # extra value allowed

# Accessing repeated param values in template
Given that calling "http://backend/collect?item=1&item=2" will return:
  """
  items: \{{request.query.item}}
  """
```

If you need to create the response from the request, it is possible to capture the parameters from the requested url:
```gherkin
# using a regex
Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
  """
  item_id: $1
  """

# using the request object with WireMock Handlebars syntax
Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
  """
  item_id: \{{request.pathSegments.6}}
  """
```

Split the path/query params to build a list dynamically:
```gherkin
Given that getting on "http://backend/v1/resource/items/(.*)" will return a List:
"""
    \{{#split request.pathSegments.6 ','}}
    - item_id: \{{this}}
    \{{/split}}
    """
When we call "http://backend/v1/resource/items/1,2,3"
Then we receive:
  """
  - item_id: 1
  - item_id: 2
  - item_id: 3
  """
```

Or even to use the posted body as an input:
```gherkin
Given that posting on "http://backend/v1/resource/items" will return a List:
"""hbs
  \{{#each (parseJson request.body)}}
  - id: \{{this.id}}
    name: nameOf\{{this.id}}
  \{{/each}}
  """
When we post on "http://backend/v1/resource/items":
  """
  - id: 1
  - id: 2
  - id: 3
  """
Then we receive:
  """
  - id: 1
    name: nameOf1
  - id: 2
    name: nameOf2
  - id: 3
    name: nameOf3
  """
```

There is also a consumption aspect on the mocks you're giving.
It means that you can call multiple times the same endpoint with same parameters and get different response depending on the amount of time you called the endpoint.
It can be useful to try out your retrying and resilience to down systems or to simulate a different backend setting for example:
```gherkin
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
```

#### URL remapping

Each mocked host will be dynamically remapped on the local WireMock server.
This means that `http://backend/users` will actually be
`http://localhost:<HttpUtils.localPort()>/_mocked/http/backend/users`

Once you have created the mock, your calls will also be remapped, so that you can call `http://backend/users` and not the remapped url.

When you call a relative url like `/endpoint`, rest-assured will automatically prefix it with `http://localhost:8080`. 
If you wish to target another host or port, you can override it programmatically with:

```java
httpSteps.setRelativeUrlRewriter(path -> "http://<host>:<port>%s".formatted(path));
```

Sometimes it can also be a bit annoying to repeat the targetted host in the tests. 

#### Assert interactions

You can assert that a defined mock has been interacted with, just like with any WireMock verification.

```gherkin
# simple
Then "http://backend/users" has received a POST:
  """
  name: bob
  """
  
# detailed  
Then "http://backend/v1/resource" has received:
  """
  method: GET
  headers:
    x-api-key: a-valid-api-key
    Authorization: Bearer MyToken
  """
  
# complete interaction
Then the interaction on "http://backend/v1/resource" was:  
  """
  request:
    method: GET
    headers:
      x-api-key: a-valid-api-key
      Authorization: Bearer MyToken
  response:
    status: OK_200
    body:
      payload:
        "something"    
  """
  
# repeated interactions
Then the interactions on "http://backend/v1/resource" were in order:
  """
  - request:
      method: GET
    response:
      status: METHOD_NOT_ALLOWED_405  
  - request:
      method: POST
    response:
      status: CREATED_201
  """

# with performance  
Then during 50ms "http://backend/endpoint" has received at most 1 GET
```

It is also possible to "save" all the received request to assert them separately:

```gherkin
When we post on "http://backend/endpoint":
  """
  containers:
    - id: 1
      zones:
        - id: 1
        - id: 2
  """
And that we post on "http://backend/endpoint":
  """
  containers:
    - id: 2
      zones:
        - id: 3
  """
Then "http://backend/endpoint" has received 2 POST payloads
And payloads[0].request.body.containers[0].zones.size == 2
And payloads[1].request.body.containers[0].zones.size == 1
```

Additionally, you can assert which requests have been received by WireMock specifying the path with eventual headers and
body through a single step. It can be useful to have a summary of every interactions in your test. Also, you can
use [Comparisons](https://github.com/Decathlon/tzatziki/blob/main/tzatziki-common/src/main/java/com/decathlon/tzatziki/utils/Comparison.java)
to assert the order in which they were received:
```gherkin
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
And the recorded interactions were at least:
"""
- method: POST
  path: http://backend/secondEndpoint?anotherParam=2&aParam=1
  headers.some-header: ?notNull
  body:
    payload:
      message: Hello little you!
- method: PATCH
  path: ?e http://backend/third(.*)
"""
And the recorded interactions were in order:
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
  path: ?e http://backend/third(.*)
"""
```

By default, any url that is called on the mock server but doesn't have a defined mock will fail your test.
That way, we ensure that your application doesn't have unwanted interactions with a service not covered by a contract.
If you want to alter this behaviour, you can do so by calling the following step:

```gherkin
* we allow unhandled mocked requests
```

#### Timeout and retry delay

The library uses Awaitility everywhere it makes sense so that we don't have to manually wait in our tests.
The default delay and timeout can be configured as a static call with:

```java
Asserts.defaultPollInterval = Duration.ofSeconds(1); // default is 10ms, it might be too often
Asserts.defaultTimeOut = Duration.ofSeconds(30); // default is 10 secs, it might not be enough
```

This is valid for most of the modules using this library as well (JPA, Kafka ...)

## Configuration

### Port Configuration

By default, the WireMock server starts on a random available port (dynamic port). In some scenarios, you may want to
specify a fixed port for the mock server.

You can specify a fixed port by setting the `tzatziki.http.port` system property.

### Mock Reset Configuration

By default, WireMock mocks are reset between tests to ensure test isolation. You can control this behavior by setting
the `resetMocksBetweenTests` static field.

```java
// Disable mock reset between tests (mocks will persist across tests)
HttpSteps.resetMocksBetweenTests = false;
```

### Concurrent Requests Configuration

The WireMock server can be configured to limit the maximum number of concurrent requests it can handle simultaneously.
This is useful for testing scenarios where you want to check order of consumption with parallel calls.

You can configure the maximum concurrent requests by setting the `tzatziki.http.max-concurrent-requests` system
property:

```java
// Set maximum concurrent requests to 1 (useful for testing sequential processing)
System.setProperty("tzatziki.http.max-concurrent-requests","1");

// Set maximum concurrent requests to 10
System.setProperty("tzatziki.http.max-concurrent-requests","10");

// Set maximum concurrent requests to 0 to disable the limit
System.setProperty("tzatziki.http.max-concurrent-requests","0");
```

## More examples

For more examples you can have a look at the tests:
https://github.com/Decathlon/tzatziki/blob/main/tzatziki-http/src/test/resources/com/decathlon/tzatziki/steps/http.feature
