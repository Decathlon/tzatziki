Tzatziki Core Library
======

## Description

This module provides steps to handle objects creation and assertion as well as time and logging management.

## Get started with this module

You need to add this dependency to your project:

```xml

<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-core</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

Once the dependency has been added to your pom, the only thing you will need is to add a `CucumberTest` class like this one in your test sources:

```java
package com.yourcompany;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "json:target/cucumber.json"}, glue = "com.decathlon.tzatziki.steps", tags = "not @ignore")
public class CucumberTest {}
```

This will allow Cucumber to be executed automatically by Junit.

### The ObjectSteps

The `com.decathlon.tzatziki.steps.ObjectSteps` class provides steps required to input and assert your objects. It will
store them in a context containing all the instances of your test run.

#### inserting data:

You can use the following steps to define a plain text content:

```gherkin
Given that content is:
"""
my plain text content
"""
```

a generic object:

```gherkin
Given that content is a Map:
"""
message: my plain text content
"""
```

or a typed object available in your test:

```gherkin
Given that tom is a User:
"""
id: 1
name: tom
"""
```

if your type can be confused with another one in your classpath, you can also use the fully qualified name.

```gherkin
Given that tom is a com.decathlon.users.model.User:
...
```

or indicate your preferred packageName using this static helper:
```java
TypeParser.setDefaultPackage("com.yourcompany");
```

All the content you will pass to the library can be provided as a YAML fragment, a JSON document 
or even a dot map table (table where the columns contain the path of the value.)

For example, for those given classes:

```java
class User {
    int id;
    String name;
    Group group;
}

class Group {
    int id;
    String name;
}
```

the following table is valid:

```gherkin
Given that users is a List<User>:
| id | name | group.id | group.name |
| 1  | bob  | 1        | admin      |
```

You can also use dot notation in order to populate a single nested field (see `tzatziki-mapper` "Dot properties" section):
```gherkin
Scenario: we can use dot-notation to specify a single nested field
    Given that yamlNest is a Nest:
    """
    subNest.bird.name: Titi
    """
    Then yamlNest contains:
    """
    subNest:
      bird:
        name: Titi
    """
    Given that jsonNest is a Nest:
    """
    {
      "subNest.bird.name": "Titi"
    }
    """
    And jsonNest contains:
    """
    {
      "subNest": {
        "bird": {
          "name": "Titi"
        }
      }
    }
    """
```

Finally, all the steps have an inline equivalent, for example:
```gherkin
Given that someVariable is:
"""
someValue
"""
```

And:
```gherkin
Given that someVariable is "someValue"
```

#### asserting data:

The library provides a wide range of methods for asserting your output.

Assuming that we define this:

```gherkin
Given that bob is:
"""
id: 1
name: bob
group:
    id: 1
    name: admin
"""
```

You can assert the whole object:

```gherkin
Then bob is equal to:
"""
id: 1
name: bob
group:
    id: 1
    name: admin
"""
```

just a fragment:

```gherkin
Then bob contains:
"""
id: 1
group:
  id: 1
"""
```

or even just one property with:

```gherkin
Then bob.id is equal to 1
```

Similarly, you can set a sub property of any object/map:
```gherkin
Given that bob.group.id is 2
```

each property can be asserted using a flag. Currently, the supported flags are:

| flag            | description                             | example                      |
|-----------------|-----------------------------------------|------------------------------|
| ?e              | matches (will treat content as a regex) | ?e .*something[abc]+\d{3}    |
| ?contains       | contains the given text                 | ?contains bob                |
| ?doesNotContain | does not contain the given text         | ?doesNotContain bob          |
| ?eq ?==         | equals                                  | ?eq test                     |
| ?gt ?>          | greater than                            | ?gt 3                        |
| ?ge ?>=         | greater or equal                        | ?ge 0                        |
| ?lt ?<          | less than                               | ?lt 10                       |
| ?le ?<=         | less or equal                           | ?le 5                        |
| ?not ?ne ?!=    | is not equal to                         | ?not bob                     |
| ?in             | is one of the given values              | ?in [1, 2, 3]                |
| ?notIn          | is not one of the given values          | ?notIn [1, 2, 3]             |
| ?isNull         | is null                                 | ?isNull                      |
| ?notNull        | is not null less or equal               | ?notNull                     |
| ?base64         | is the base64 of                        | ?base64                      |
| ?isUUID         | is an UUID                              | ?isUUID                      |
| ?before         | is before the given instant             | ?before {{@now}}             |
| ?after          | is after the given instant              | ?after {{@now}}              |
| ?is Type        | can be parsed as the given Type         | ?is Boolean                  |

an example:
```gherkin
Then bob is equal to:
"""
id: 1
name: ?e b.*
group: ?notNull
"""
```

Objects, Lists and Maps can be compared using different methods:

| expression                     | description                                              |
|--------------------------------|----------------------------------------------------------|
| contains                       | actual contains at least the expected elements           |
| contains in order              | actual contains at least the expected elements in order  |
| contains only                  | actual contains only the expected elements               |
| contains only and in order     | actual contains only the expected elements in order      |
| contains exactly               | actual contains exactly the expected elements            |
| is equal to                    | actual is equal to the expected elements (same values)   |
| is exactly                     | actual is exactly the expected elements (literally)      |

For example:
```gherkin
When users is a List<User>:
"""
- id: 1
  name: tom
- id: 2
  name: lisa   
"""
Then users contains at least:
"""
- id: 1
  name: tom
"""
Then users contains at least and in order:
"""
- id: 1
  name: tom
- id: 2
  name: lisa   
"""
```

#### Fields, Methods, Arrays and Strings

you can access any field or method on your objects. The library will try its best to find a relevant value.
Elements in arrays and lists can be accessed using `[index]` and substring with `[from-to?]` (if `to` is omitted, .length() is used)

For example:
```gherkin
When users is a List<User>:
"""
- id: 1
  name: tom
- id: 2
  name: lisa   
"""
Then users[0].id == tom
And users.size == 2
And users[1].name[2-] == "sa"
And users[1].name[0-2] == "li"

# let's change a user name
Given that users[0].name is "bob"
```

## Templating

All the content of our tests is templated using [Handlebars](https://github.com/jknack/handlebars.java). 
Each input and output is considered as a template where the current context is injected.
This applies to any String content, including the parameters that are inlined in the step itself.

```gherkin
Given that someName is "bob"
And that bob is a User:
"""
id: 1
name: {{someName}}
"""
Then bob.name is equal to "bob"
```

A useful feature is that you can actually assign variables while templating them, like:
```gherkin
Given that bob is a User:
"""
id: 1
name: {{{[userName: bob]}}}
"""
Then bob.name is equal to "bob"
And userName is equal to "bob"
```

*notice that you need to surround your expression with `{{{[ expression ]}}}` to have Handlebars deal with the whitespaces*

You can also use some built-in-handlebars helpers, or use some custom helpers for Tzatziki. One is used for example to concatenate multiple arrays :
```gherkin
Given that myFirstArray is:
"""
- firstItem
- secondItem
"""
And that mySecondArray is:
"""
- thirdItem
- fourthItem
"""

When resultArray is:
"""
{{#concat myFirstArray mySecondArray}}
  {{this}}
{{/concat}}
"""

Then resultArray is equal to:
"""
[firstItem, secondItem, thirdItem, fourthItem]
"""
```

Other custom helpers are foreach (loop through array), split (split a String by symbol), math (compute some value), noIndent (remove indent right before processing to improve visibility) and conditional helpers (to compare values and output conditionally)

## Time management

This library uses [Natty](http://natty.joestelmach.com/try.jsp) to express time human friendly way.
In addition, the time is set at the beginning of the test run and won't move until the next test.
A variable `now` is automatically added to the test context.

If you want your test to use a specific time you can use the following step:
```gherkin
Given that the current time is the first Sunday of November 2020 at midnight
Then now is equal to "2020-11-01T00:00:00Z"
```

This now can be templated in any content you pass to the library:
```gherkin
Given that bob is a User:
"""
id: 1
name: bob
last_login: {{@now}}  
"""
```

However, this wouldn't be useful if we couldn't pass anyelse than `now`, right? 
To have the Handlebars' context resolve the times for us, we just need to prefix it with `@`:
```gherkin
Given that bob is a User:
"""
id: 1
name: bob
last_login: {{{[@10 mins ago]}}}  
"""
```

By default, the time is added to the context as an Instant. But we can also specify the output format.

Here is a list of the supported date format and what would have in the context:

| input                                                                        | output                               |
|------------------------------------------------------------------------------|--------------------------------------|
| the first Sunday of November 2020 at midnight                                | 2020-11-01T00:00:00Z                 |
| first Sunday of November 2020 at midnight as an instant                      | 2020-11-01T00:00:00Z                 |
| first Sunday of November 2020 at midnight in milliseconds                    | 1604188800000                        |
| first Sunday of November 2020 at midnight as a long                          | 1604188800000                        |
| first Sunday of November 2020 at midnight as a timestamp                     | 1604188800000                        |
| first Sunday of November 2020 at midnight in seconds                         | 1604188800                           |
| first Sunday of November 2020 at midnight (Europe/Paris) as a timestamp      | 1604185200000                        |
| first Sunday of November 2020 at midnight (Europe/Paris) as a localdate      | 2020-11-01                           |
| first Sunday of November 2020 at midnight (Europe/Paris) as a localdatetime  | 2020-11-01T00:00                     |
| first Sunday of November 2020 at midnight (Europe/Paris) as a zoneddatetime  | 2020-11-01T00:00+01:00               |
| first Sunday of November 2020 at midnight (Europe/Paris) as a offsetdatetime | 2020-11-01T00:00+01:00               |
| first Sunday of November 2020 as a formatted date YYYY-MM-dd                 | 2020-11-01                           |

if you need to add a time format that is not in the list, you can do it by using the following helper:

```java
Time.addCustomTypeAdapter("minguodate", (date, zoneId) -> MinguoDate.from(date.toInstant().atZone(zoneId).toLocalDate()));
```

The following step should now work:
```gherkin
Given that bob is a User:
"""
id: 1
name: bob
last_login: {{{[@10 mins ago as a minguodate]}}}
"""
```

## File IO

You can write to and read from a file using the following syntax :
```gherkin
# WRITE
Given that we output in "path/to/file":
"""yml
id: 1
name: bob
"""

# READ 
When bob is "{{{[&path/to/file]}}}"
Then bob is equal to:
"""yml
id: 1
name: bob
"""
```

Notice the syntax `{{{[&path/to/file]}}}` : you can reuse this in your custom steps as long as you `objectSteps.resolve` the content.

## Guards

You can add guards to a given step in order to modify its behaviour. To do so, you have to add the
constant `com.decathlon.tzatziki.utils.Guard.GUARD` between the optional `com.decathlon.tzatziki.utils.Patterns.THAT`
and your custom step pattern.

```java
@Then(THAT + GUARD + VARIABLE + " (?:==|is equal to) " + NUMBER + "$")
```

It will allow you to use the below guards :

#### Inverting a test

Any test can be inverted by prefixing it with `it is not true that`, for example:

```gherkin
Given that user1 is a User:
"""
id: 1
name: bob
"""
Then it is not true that user1.name is equal to "tom"
```

#### Conditional execution

Inspired by the guard mechanism in Scala, you can subject the execution of any step to a predicate.

The syntax of the prefix is `if <predicate> =>`, for example:

```gherkin
Given that user is a User:
"""
id: 1
name: bob
"""
# this step will be skipped
Then if user.id > 1 => user.name is equal to "tom"
But if user.id == 1 => user.name is equal to "bob"
```

This can be handy for not duplicating scenarios just for one additional step.

Moreover, you can use the `else` or `otherwise` keyword to execute a step if the predicate is not met.
```gherkin
Given that condition is "<ifCondition>"
When if <ifCondition> == true => ran is "if"
* else ran is "else"
```

Note that it will take the latest condition evaluation to know wether to execute the step or not.

#### Delay a step asynchronously

Any step can be started asynchronously after a given amount of time by prefixing it with `after <amount>ms`, for example:
```gherkin
Given that after 100ms user is a User:
    """
    id: 1
    name: bob
    """
```
This will start a task on a separate thread and set the value of the user to be the given one only in 100ms.
This can be useful to test the resilience of your code.

*Note: If your asynchronous step throws an exception, this one will be collected at the end of your scenario and fail it.*

#### Test that something becomes true within or during a given a time

You can test that something becomes true within a given time by prefixing your step with `within <amount>ms`, for example:
```gherkin
Then within 100ms user is equal to:
    """
    id: 1
    name: bob
    """
```

This will wait until the assertion is true! If you want to verify that the assertion is true for the entire period, you can prefix your step with `during <amount>ms`:
```gherkin
Then during 100ms user is equal to:
    """
    id: 1
    name: bob
    """
```

#### Catch an exception

You can catch an exception and assert it by prefixing your step with `an exception <ExceptionType> is thrown when`:
```gherkin
Then an exception MismatchedInputException is thrown when badlyTypedObject is a User:
  """json
  a terribly incorrect json
  """
And exception.message is equal to "?contains Cannot construct instance of `com.decathlon.tzatziki.User`"
```

*Note: the name of the exception is optional, if not provided the exception will be saved as `_exception`*

### Chain multiple guards

It is also possible to chain multiple guards in order to achieve a specific behaviour. A typical example would be to
execute a statement only based on a certain condition and assert something during a given period of time (asynchronous
execution for ex.):
```gherkin
Given that if <shouldDoTask> == true => after 100ms taskDone is "true"
Then if <shouldDoTask> == true => within 110ms taskDone is equal to "true"

Examples:
    | shouldDoTask |
    | true         |
    | false        |
```

Moreover, you can chain any number of guards, the only rule is to append guards with a space between them.

### Internal variables in the context

Some internal variables are in the context so that you can access them in a simple way.

By default, if you use a `Scenario Template`, the values from your examples' table are not available in the `Background` of your feature.
In Tzatziki, the ObjectSteps will actually extract them, and export them as the variable `_examples` so that the following will work:

```gherkin
Background: 
  Given that we call "{{_examples.url}}"

Scenario Template:
  When we ...
  Then we receive ...
    ...
  
  Example:
    | url                      |
    | http://backend1/endpoint |
    | http://backend2/endpoint |
```

Following the same logic, you can access the Scenario object itself as `_scenario`:

```gherkin
@someTag
Scenario: we can access the tags in a scenario
  * _scenario.sourceTagNames[0] == "@someTag"
```

write and read an Environment variable at runtime using `_env`:

```gherkin
Scenario: we can access the ENVs from the test
  # see com.decathlon.tzatziki.utils.Env to see how we can set an environment variable at runtime
  Given that _env.TEST = "something"
  Then _env.TEST is equal to "something"
```

or the system properties using `_properties`:

```gherkin
Scenario: we can access the system properties from the test
  Given that _properties.test = "something"
  Then _properties.test is equal to "something"
```

Generally, Tzatziki internal variables will be prefixed with `_` so that they don't collide with the variables of your tests!

### Method calls

#### Inline method call
A method can be called within a variable or property assignment through the same syntax as a Java call.
You may eventually add curly brackets around variables which should get extracted from the context and injected as method input
```gherkin
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
```

Notes:
- The order in which the evaluation is done remains the same as for the property-retrieving behaviour 
  - You can see it through the `bobIsInBefore` and `bobIsInAfter` properties: while the same statement is evaluated twice, the result are different because the `List.set` is made in-between due to property ordering
- The variable `users` is explicitly typed to `List<String>` in order to have `List.class` methods available


#### Full-description call to any method
You can call any method and specify the wanted parameters through a one-step described method call.
You can then retrieve result of the method call (_method_output) or catch an exception through guard if the invocation went wrong.

##### Without parameter
This snippet is used to call `List.size` over an instantiated list.
```gherkin
Given that aList is a List:
"""
- hello
- mr
"""
When the method size of aList is called
```

##### Assert the result of the invocation
The output of a method (= returned object) can be asserted through this step, keeping the type of the object.
```gherkin
Then _method_output.class is equal to:
"""
com.decathlon.tzatziki.User
"""
And _method_output is equal to:
"""
id: 1
name: bob
"""
```

If anything went wrong and an exception was thrown out during the invocation, you can also assert it through "an exception is thrown" guard.
```gherkin
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
```

##### With parameters
There is two ways to call a method with parameters. 

Respect the same parameter order as the wanted method. In this case you can simply provide a map with any name as key and value with the actual wanted parameter value. (#1, #2, #3)
The parameter mapping can also be done using name of the parameters through introspection (requires `-parameters` option on introspected class compilation). (#4)
```gherkin
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
  | methodCalled | params                               | expectedReturn | expectedListState                |
  | get          | {"anyName":0}                        | hello          | {"wrapper":["hello","bob"]}      | # parameter matching by order
  | get          | {"anotherName":1}                    | bob            | {"wrapper":["hello","bob"]}      | # parameter matching by order
  | add          | {"byArgOrder1":1,"byArgOrder2":"mr"} | ?isNull        | {"wrapper":["hello","mr","bob"]} | # parameter matching by order
  | add          | {"element":"mr","index":1}           | ?isNull        | {"wrapper":["hello","mr","bob"]} | # parameter matching by introspection over parameter name
```
Note that with the parameter-order matching strategy, if multiples candidates are found (with distinct parameter type for examples), all candidates will be tried out sequentially until the Mapper manages to fill out every parameter.

##### Call a static method
Static methods can also be called by specifying the class on which to call the method instead of specifying an instance from the context. The same rules as above are used when it comes to specify parameters.
```gherkin
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
"""
```

## More examples

For more examples you can have a look at the tests:
https://github.com/Decathlon/tzatziki/tree/main/tzatziki-core/src/test/resources/com/decathlon/tzatziki/steps/

