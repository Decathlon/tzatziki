Tzatziki common
======

## Description

This module contains common util classes that are not directly linked to cucumber.


## Get started with this module

You need to add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-common</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

## Content of this module

- **com.decathlon.tzatziki.utils.Asserts:** An assertion helper that can compare all type of objects and assert if one is contained in the other (a bit like JSONAssert)
- **com.decathlon.tzatziki.utils.Comparison:** Parser for the `Asserts` class, so that `contains in order` can be mapped to `Asserts.containsInOrder(expected, actual)`
- **com.decathlon.tzatziki.utils.Matchers:** Hamcrest matchers wrapping the `Asserts` class.
- **com.decathlon.tzatziki.utils.Env:** To set an environment variable at runtime
- **com.decathlon.tzatziki.utils.Mapper:** Configurable mapper containing a JSON and a YAML Jackson instance
- **com.decathlon.tzatziki.utils.Time:** Human friendly time parser wrapping Natty
- **com.decathlon.tzatziki.utils.TypeParser:** Class lookups (Also supports parameterized types)
