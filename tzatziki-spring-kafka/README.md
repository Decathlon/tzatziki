Tzatziki Spring Kafka Library
======

## Description

This module provides the base dependencies as well as steps to start testing the integration of your Spring app with your Kafka.

## Get started with this module

You need to add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-spring-kafka</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

## Adding the kafka configuration

we will assume that you followed the [readme from the spring module](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-spring)

The only thing you need to do is to add the embedded kafka instance as well as the configuration to your test Steps.

```java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = TestApplication.class)
@ContextConfiguration(initializers = TestApplicationSteps.Initializer.class)
public class TestApplicationSteps {

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            KafkaSteps.start(); // this will start the embedded kafka
            TestPropertyValues.of(
                    "spring.kafka.bootstrap-servers=" + KafkaSteps.bootstrapServers(),
                    "spring.kafka.properties.schema.registry.url=" + KafkaSteps.schemaRegistryUrl() // an optional in-memory schema registry
            ).applyTo(configurableApplicationContext.getEnvironment());
        }
    }
}
```

*Notes: The class `com.decathlon.tzatziki.kafka.SchemaRegistry` 
wraps the class `io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient` using mockserver to make it available 
over http. So you can access it like your regular schema registry...*

## Defining an avro schema

If you want, you can add avro schemas dynamically by using this step:
```gherkin
Given this avro schema:
  """
  type: record
  name: user
  fields:
    - name: id
      type: int
    - name: name
      type: string
  """
```

## Publishing messages

Messages can be published:
 - **synchronously**: the step will wait for your message to be consumed by a listener/consumer.
 - **asynchronously**: the step will just publish the message in the topic, and the test will move on to the next step.

Assuming that you have defined the user avro schema above, and that your application (or test) 
provides a `KafkaTemplate<String, GenericRecord>` as well as a `ConsumerFactory<String, GenericRecord>`, 
you can now use:
```gherkin
# asynchronously
When this user is published on the users topic:
  """
  id: 1
  name: bob
  """

# synchronously  
When this user is consumed from the users topic:
  """
  id: 1
  name: bob
  """
```

The later step will block until the messages are consumed, regardless of the result.

If you need to block until the messages are successfully consumed, you can use:
```gherkin
# synchronously and not throwing an exception
When this user is successfully consumed from the users topic:
  """
  id: 1
  name: bob
  """
```

Alternatively, you can pass a list of messages:
```gherkin
# as a yaml / json
When these users are consumed from the users topic:
  """
  - id: 1
    name: bob
  - id: 2
    name: lisa
  """

# or a table  
When these users are consumed from the users topic:
  | id | name |
  | 1  | bob  |
  | 2  | lisa |
```

and specify the headers:
```gherkin
When this user is published on the exposed-users topic:
  """
  headers:
    uuid: some-id
  value:
    id: 1
  name: bob
  """
```

## Asserting messages

You can assert that a topic contains a given value:
```gherkin
Then the users topic contains this user:
  """
  id: 1
  name: bob
  """
```

many messages
```gherkin
Then the users topic contains these users:
  """
  - id: 1
    name: bob
  - id: 2
    name: lisa 
  """
```

assert the headers:
```gherkin
Then the users topic contains this user:
  """
  headers:
    uuid: some-id
  value:
    id: 1
    name: bob
  """
```

or just a given amount of message:
```gherkin
Then the users topic contains 3 users
```

If you do not use avro in your application, you can use `json message` in place of `user`, like:
```gherkin
Then the json-users topic contains this json message:
  """
  id: 1
  name: bob
  """
```

Just make sure that your application provides a `KafkaTemplate<String, String>` and a `ConsumerFactory<String, String>`.

## Interacting directly with the topics and consumers

You can set the current offset of a consumer directly with the following step:

```gherkin
But if the current offset of user-group-id on the topic users-with-headers is 2
```

If you need to manually wait for a group-id to have a fully consumed a topic, you can do so with:

```gherkin
When the users-group-id group id has fully consumed the json-users-input topic
```

## A word about offsets

All the topics will look like they are empty at the beginning of each test. 

However, when testing kafka, it is really hard to start fresh between each test. 
There is no such thing as a `TRUNCATE` method, and re-creating the topic turned out to be both time-consuming and really unstable.

So to achieve this we actually intercept all the methods reading or manipulating the offsets of our topic (`poll`, `seek`, `seekToBeginning` etc.)

At the end of each test we write down the current offset and set it as the new virtual beginning of the topic for the next test.
Each message polled from the topic will then be rewritten to offset the position by this virtual beginning. Each method call manipulating
the topic will be offset as well.

That way, the offset of a message is deterministic and can be asserted in your tests. 
For example, the RecordMetadata of the first user of our test will always be `users-0@0`, no matter how many tests we had before that test.

For that reason, if you have output topics that you don't actively consume in your app, but still want to assert in some tests, 
you will need to tell the library to seek through nonetheless. To do so, add this to the Initializer in your steps:

```java
KafkaSteps.autoSeekTopics("<name of your topic>", ...);
```

# More examples

For more examples you can have a look at the tests:
https://github.com/Decathlon/tzatziki/blob/main/tzatziki-spring-kafka/src/test/resources/com/decathlon/tzatziki/steps/kafka.feature
