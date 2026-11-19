Tzatziki testng
======

## Description

This module provides the proper dependencies to run your tests using testng and not junit. 
This will allow you to run the tests in parallel!

This is really useful if you are testing your staging environment, but it can really make writing tests and debugging your CI pipeline a lot harder.

Cucumber 8 deprecates its TestNG integration in favor of the JUnit Platform engine. Existing TestNG suites remain
supported, but new suites should use the [JUnit Platform runner](../MIGRATION_JUNIT5.md). Existing parallel TestNG
runners must update their `scenarios` override to accept an `ITestContext`, as shown below.

## Get started with this module

You need to add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-testng</artifactId>
    <version>1.0.x</version>
    <scope>test</scope>
</dependency>
```

## Modify your project!

you need to replace your Bootstrap class with this one:

```java
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.ITestContext;
import org.testng.annotations.DataProvider;

@CucumberOptions(plugin = {"pretty", "json:target/cucumber.json"}, glue = "com.decathlon.tzatziki.steps")
public class CucumberTest extends AbstractTestNGCucumberTests {

    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios(ITestContext context) {
        return super.scenarios(context);
    }
}
```

and add this option to your surefire plugin (assuming a parallelism of 10):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.0.0-M9</version>
    <configuration>
        <parallel>methods</parallel>
        <threadCount>10</threadCount>
    </configuration>
    <dependencies>
        <dependency>
            <groupId>org.apache.maven.surefire</groupId>
            <artifactId>surefire-testng</artifactId>
            <version>3.0.0-M9</version>
        </dependency>
    </dependencies>
</plugin>
```

And that's it!
