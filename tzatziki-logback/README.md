Tzatziki Logback Library
======

## Description

This module provides steps to manage the log level of your tests assuming that you are using logback.

## Get started with this module

You need to add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-logback</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

### The LoggingSteps

The `com.decathlon.tzatziki.steps.LoggerSteps` class provides steps that will allow you to configure your logger.

You can set the log level of all your logs with:

```gherkin
Given a root logger set to DEBUG
```

or for a package only:

```gherkin
Given a com.yourcompany logger set to DEBUG
```

and to assert that the logs contain something:

```gherkin
Then the logs contain:
  """
  - ?e .* some lines
  """
```

## More examples

For more examples you can have a look at the tests:
https://github.com/Decathlon/tzatziki/tree/main/tzatziki-logback/src/test/resources/com/decathlon/tzatziki/steps/logger.feature

