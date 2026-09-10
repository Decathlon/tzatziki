Tzatziki Jackson 3
======

## Description

This module contains the Jackson 3 (`tools.jackson`) implementation of the `MapperDelegate` required by Tzatziki.

## Get started with this module

Add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-jackson3</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

Tzatziki will then use Jackson 3 for serialization and deserialization in your tests.

## Delegate selection

Mapper implementations are discovered through Java's `ServiceLoader`:

- Jackson 3 is selected whenever `tzatziki-jackson3` is on the classpath.
- Jackson 2 is selected when `tzatziki-jackson3` is absent and `tzatziki-jackson` is present.

When both implementations are present, Jackson 3 is selected automatically.
