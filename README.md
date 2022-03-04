Tzatziki Steps Library
======

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.decathlon.tzatziki/tzatziki-parent/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.decathlon.tzatziki/tzatziki-parent)
![Build](https://github.com/Decathlon/tzatziki/workflows/Build/badge.svg)
[![codecov](https://codecov.io/gh/Decathlon/tzatziki/branch/main/graph/badge.svg)](https://codecov.io/gh/Decathlon/tzatziki)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
![lifecycle: beta](https://img.shields.io/badge/lifecycle-beta-509bf5.svg)

This project is a collection of ready-to-use Cucumber steps making it easy to TDD Java microservices by focusing on an
outside-in testing strategy.

## Wait, Cucumber?

*You are a Cucumber veteran? ... jump directly to [Content of this project](#content-of-this-project)*

Otherwise, here is what [wikipedia](https://en.wikipedia.org/wiki/Cucumber_(software)) says:

> Cucumber is a software tool used by computer programmers for testing other software.
> It runs automated acceptance tests written in a behavior-driven development (BDD) style.
> Central to the Cucumber BDD approach is its plain language parser called Gherkin.
> It allows expected software behaviors to be specified in a logical language that customers can understand.
> As such, Cucumber allows the execution of feature documentation written in business-facing text.

*What does it mean to us developers?*

Cucumber provides a mapping between humanly readable test files written in a language called Gherkin and their JUnit
implementations. You can think about it as a partition that will execute pieces of JUnit code.

*Why using Cucumber?*

By creating a separation between a test expression and its implementation, the resulting Cucumber test tends to be a bit
more readable than its JUnit counterpart. Additionaly, the reusability of each JUnit implementation is really high, and
over time only the Gherkin needs to be added to test a new feature.

*Okay ... so how does it work?*

### Getting started with Cucumber in 5 mins

The Cucumber tests are written in `.feature` files. Most of the IDEs have support for writting, running and debugging
cucumber tests. Since deep down they are just JUnit tests, once they are running everything should be the same: code
coverage, reporting etc.

The structure of a `.feature` file is the following:

```gherkin
Feature: the name of your feature
  Here you can put some comments describing your feature

  Background: some stuff that needs to be done for every scenario
    * a system is running

  @this-is-a-tag
  Scenario: Change a state in the system
  As a User I expect to go from A to C if B happens

    Given a state A
    When B happens
    # we can also put comments if things need a bit of explanation
    Then C is the new state of the system

  Scenario: some other scenario
  ...
```

The lines starting with `Given, When, Then` are called Steps. Additional Steps keywords are `And` and `But` (`*` is also
accepted). Those keywords don't really have a functional meaning, they are just there for us to write nice tests. We
could start every step with `*` and the output of the test would be exactly the same. However, you should choose the one
fitting the most the intent of the step you are writing. A big part of the idea behind using Gherkin, is that the tests
are the specifications of the code, so it should be enough to read them to understand the product they test.

An optional `Background` section can be added at the beginning. The steps in it will be repeated before any scenario in
the file, like a method annotated with `@org.junit.Before`.

Each Step has an implementation in plain Java that is annotated with a regular expression matching the step.

So for example:

```gherkin
Given that we do something
```

will have the following implementation:

```java
@Given("that we do something")
public void do_something(){
  // do something here
}
```

Cucumber can extract parameters directly from a step so that:

```gherkin
Given a user named "bob"
```

can be implemented as:

```java
@Given("a user named \"(.*)\"")
public void a_user_named(String name){
  // create a user with that name
}
```

But it also supports multiline arguments:

```gherkin
Given the following configuration file:
  """
  property: value
  """
```

```java
@Given("the following configuration file:")
public void the_following_configuration_file(String content){
  // do something with the file content
}
```

as well as tables:

```gherkin
Given the following users:
  | id  | name    |
  | 1   | bob     |
  | 2   | alice   |
```

```java
@Given("the following users")
public void the_following_users(List<Map<String, String>> users){
  // do something with those users
}
```

Those Java methods need to be added to a Steps class, typically something like `LocalSteps`. Keep in mind that for
technical reasons Cucumber will not allow you to extend those steps. Instead, the framework will enforce composition,
and if any class extending a Steps class is detected, an exception will be thrown.

Cucumber also comes with support for injection frameworks, so all your dependencies will be properly instantiated and
injected at runtime, per scenario.

Note that your `@org.junit.Before` and `@org.junit.After` annotations won't work in your steps. You need to use the
Cucumber equivalent: `@cucumber.api.java.Before` and `@cucumber.api.java.After`

Example:

```java
public class BaseSteps {

    @Before
    public void before() {
        // something to run before each scenario
    }

    @Given("that we do something")
    public void do_something() {
        // do something here
    }
}

public class LocalSteps {

    private final BaseSteps baseSteps;

    public LocalSteps(BaseSteps baseSteps) {
        this.baseSteps = baseSteps;
    }

    @Given("a user named \"(.*)\"")
    public void a_user_named(String name) {
        baseSteps.do_something();
        // create a user with that name
    }

    @Given("the following users")
    public void the_following_users(List<Map<String, String>> users) {
        // do something with those users
    }

    @After
    public void after() {
        // something to run after each scenario
    }
}
```

Finally, in order to have JUnit execute our Cucumber tests we need a runner:

```java
package com.yourcompany.yourproject;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = "pretty")
public class CucumberTest {
}
```

By default, cucumber will look for `.feature` files in the same directory structure than the java runner. However, this
can be configured using the `features` property on the `@io.cucumber.junit.CucumberOptions` annotation. In addition, it
will also look for Java classes containing steps next to the runner and this can also be configured by using the `glues`
property on the same annotation.

> Tip:
> Sometimes it can be hard to come up with the implementation steps...
> but if you start by typing your new step in your feature file and then execute the scenario, Cucumber will output an implementation for you:

```
  Undefined step: Given something else that is not yet implemented

  Skipped step

  Skipped step

  Skipped step

  1 Scenarios (1 undefined)
  5 Steps (3 skipped, 1 undefined, 1 passed)
  0m0.250s


  You can implement missing steps with the snippets below:

  @Given("^something else that is not yet implemented")
  public void something_else_that_is_not_yet_implemented() throws Throwable {
      // Write code here that turns the phrase above into concrete actions
      throw new PendingException();
  }
```

## Content of this project

This repository contains several libraries, each one having its own tutorial and documentation when applicable:

- [tzatziki-mapper](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-mapper) : module containing only the Mapper
  interface.
- [tzatziki-jackson](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-jackson) : Jackson implementation of the
  Mapper.
- [tzatziki-common](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-common) : dependency module containing the
  base classes for the core library, but without cucumber.
- [tzatziki-core](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-core) : the core library, provides support of
  our test instances as well as input/output and time management.
- [tzatziki-logback](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-logback) : the logging library, provides
  support for dynamically configuring the log levels in your tests.
- [mockfaster](https://github.com/Decathlon/tzatziki/tree/main/mockfaster) : static wrapper around mockserver to reduce
  the time taken by redefining mocks.
- [tzatziki-http](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-http) : http library encapsulating both
  rest-assured and mockserver.
- [tzatziki-spring](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-spring) : base library to start a spring
  service
- [tzatziki-spring-jpa](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-spring-jpa) : support for spring jpa to
  insert and assert data in the database.
- [tzatziki-spring-kafka](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-spring-kafka) : support for spring
  kafka listener and consumers.

## Support

We welcome [contributions](https://github.com/Decathlon/tzatziki/tree/main/CONTRIBUTING.md), opinions, bug reports and
feature requests!