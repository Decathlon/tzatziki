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

By default, any url that is called on the mockserver but doesn't have a defined mock will fail your test.
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
