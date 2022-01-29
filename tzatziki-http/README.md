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

Make sure that you have the bootstrap class described in the [Core README]()

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

Internally, Mockserver is used for defining mocks and asserting interactions.

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

If you need to create the response from the request, it is possible to capture the parameters from the requested url:
```gherkin
# using a regex
Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
  """
  item_id: $1
  """

# using the request passed in the context
Given that getting on "http://backend/v1/resource/item/(\d+)" will return:
  """
  item_id: {{request.pathParameterList.0.values.0}}
  """
```

Split the path/query params to build a list dynamically:
```gherkin
Given that getting on "http://backend/v1/resource/items/(.*)" will return a List:
  """
  {{#split request.pathParameterList.0.values.0.value [,]}}
  - item_id: {{this}}
  {{/split}}
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
  """
  {{#foreach request.body}}
  - id: {{this.id}}
    name: nameOf{{this.id}}
  {{/foreach}}
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

#### URL remapping

Each mocked host will be dynamically remapped on the local mockserver.
This means that `http://backend/users` will actually be `http://localhost:{{mockserver.port}}/http/backend/users`

Once you have created the mock, your calls will also be remapped, so that you can call `http://backend/users` and not the remapped url.

#### Assert interactions

You can assert that a defined mock has been interacted with, the same way you would do it with mockserver.

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
Then "http://backend/endpoint" has received at most 1 GET within 50ms
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
And payloads[0].body.json.containers[0].zones.size == 2
And payloads[1].body.json.containers[0].zones.size == 1
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

## More examples

For more examples you can have a look at the tests:
https://github.com/Decathlon/tzatziki/blob/main/tzatziki-http/src/test/resources/com/decathlon/tzatziki/steps/http.feature
