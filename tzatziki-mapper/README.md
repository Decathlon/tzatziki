Tzatziki mapper
======

## Description

This module contains the Mapper interface that is used in all the tzatziki modules. The default implementation is the
Jackson implementation provided by [tzatziki-jackson](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-jackson)
but this module allows you to add your own (for example, if you are using Gson).

## Get started with this module

You need to add this dependency to your project:

```xml

<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-mapper</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

## Get your implementation picked up by Tzatziki

Your implementation needs to implement `com.decathlon.tzatziki.utils.MapperDelegate`, have a no-arg public constructor,
and for the ServiceLoader to find your class you will need to add a file similar
to [this one](https://github.com/Decathlon/tzatziki/tree/main/tzatziki-jackson/src/main/resources/META-INF/services/com.decathlon.tzatziki.utils.MapperDelegate)
to your project/module.
