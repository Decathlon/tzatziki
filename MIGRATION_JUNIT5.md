# Migration Guide: Upgrading to Tzatziki with JUnit 5

This guide provides step-by-step instructions for migrating your test suites from JUnit 4 to JUnit 5 when using Tzatziki.

## Overview

As of version 3;x, Tzatziki has migrated from JUnit 4 to JUnit 5 Platform. While Cucumber functionality remains unchanged, the way you configure and run your Cucumber tests has been updated.

## ⚠️ Critical: Verify Your Tests Actually Run

**After completing the migration, you MUST verify that your tests are actually executing.**

A misconfigured Cucumber + JUnit 5 test suite can result in:
- ✅ **Successful build** (exit code 0)
- ❌ **Zero tests executed** (silently skipped)

This can create a false sense of security where your CI/CD pipeline appears green, but no tests are actually running.

**Always check the test output for lines like:**
```
Tests run: 0, Failures: 0, Errors: 0, Skipped: 0
```

If you see `Tests run: 0`, your tests are not being discovered. See the [Troubleshooting](#troubleshooting) section below for common causes and solutions.

**Recommended verification steps:**
1. Run `mvn clean test` and check the output for the actual number of scenarios executed
2. Temporarily break a test to ensure failures are detected
3. Compare the number of tests executed before and after migration

## What Changed

- **Cucumber Integration**: Replaced `cucumber-junit` with `cucumber-junit-platform-engine`
- **Test Runners**: Replaced JUnit 4 `@RunWith(Cucumber.class)` with JUnit 5 `@Suite` annotations
- **Dependencies**: Added JUnit Platform Suite dependencies
- **Assertions**: Internal Tzatziki assertions now use JUnit 5 APIs (no impact on your tests)

## Migration Steps

### 1. Update Your Dependencies

#### Remove JUnit 4 Dependencies

Remove these dependencies from your `pom.xml`:

```xml
<!-- REMOVE -->
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-junit</artifactId>
</dependency>

<dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
</dependency>
```

#### Add JUnit 5 Platform Dependencies

Add these dependencies to your `pom.xml`:

```xml
<!-- ADD -->
<dependency>
    <groupId>io.cucumber</groupId>
    <artifactId>cucumber-junit-platform-engine</artifactId>
    <version>${cucumber.version}</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.junit.platform</groupId>
    <artifactId>junit-platform-suite-api</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.junit.platform</groupId>
    <artifactId>junit-platform-suite-engine</artifactId>
    <scope>test</scope>
</dependency>
```

**Optional**: If you're using the JUnit BOM for dependency management (recommended):

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.junit</groupId>
            <artifactId>junit-bom</artifactId>
            <version>-- latest version --</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 2. Update Your Cucumber Test Runners

#### JUnit 4 Style (Old)

```java
package com.example;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
    features = "src/test/resources/features",
    glue = "com.example.steps",
    plugin = {"pretty", "json:target/cucumber.json"}
)
public class CucumberTest {
}
```

#### JUnit 5 Style (New)

```java
package com.example;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber.json")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.steps")
public class CucumberTest {
}
```

#### Key Changes:

1. **Imports**: Replace JUnit 4 imports with JUnit Platform Suite annotations
2. **Annotations**: 
   - Replace `@RunWith(Cucumber.class)` with `@Suite` and `@IncludeEngines("cucumber")`
   - Replace `@CucumberOptions` with `@ConfigurationParameter` annotations
3. **Feature Selection**:
   - Use `@SelectClasspathResource("features")` for features in a `features/` directory
   - Use `@SelectDirectories("src/test/resources/path/to/features")` for nested paths
4. **Configuration**: Use constants from `io.cucumber.junit.platform.engine.Constants`

### 3. Additional Configuration Options

#### Filtering Tags

```java
import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;

@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @ignore")
```

#### Object Factory (for Spring Integration)

```java
import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;

@ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME, value = "io.cucumber.spring.SpringFactory")
```

#### Selecting Specific Features

```java
// For features in a standard directory
@SelectClasspathResource("features")

// For features in nested package structure
@SelectDirectories("src/test/resources/com/example/features")

// For a specific feature file
@SelectFile("src/test/resources/features/my-feature.feature")
```

### 4. Update Unit Tests (If Applicable)

If you have unit tests in your project that use JUnit 4, consider migrating them to JUnit 5:

#### JUnit 4 (Old)

```java
import org.junit.Test;
import static org.junit.Assert.*;

public class MyTest {
    @Test
    public void testSomething() {
        assertEquals("expected", actual);
    }
}
```

#### JUnit 5 (New)

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MyTest {
    @Test
    void testSomething() {
        assertEquals("expected", actual);
    }
}
```

## Complete Example

Here's a complete example showing the migration for a typical Tzatziki test runner:

### Before (JUnit 4)

```java
package com.example.tests;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
    features = "classpath:features",
    glue = {"com.decathlon.tzatziki.steps", "com.example.steps"},
    plugin = {"pretty", "json:target/cucumber.json"},
    tags = "not @ignore"
)
public class AcceptanceTest {
}
```

### After (JUnit 5)

```java
package com.example.tests;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber.json")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.decathlon.tzatziki.steps, com.example.steps")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @ignore")
public class AcceptanceTest {
}
```

## Troubleshooting

### Tests Not Discovered

**Problem**: `NoTestsDiscoveredException: Suite [...] did not discover any tests`

**Solutions**:
1. Verify your feature file location matches the `@SelectClasspathResource` or `@SelectDirectories` path
2. For nested paths like `com/example/features`, use `@SelectDirectories("src/test/resources/com/example/features")`
3. For simple directories like `features`, use `@SelectClasspathResource("features")`
4. Ensure feature files have the `.feature` extension

### Maven Surefire Configuration

Ensure your Maven Surefire plugin version is 3.0.0 or higher for proper JUnit 5 support:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.4</version>
</plugin>
```

### IDE Support

Most modern IDEs support JUnit 5 out of the box. If tests aren't running:

1. **IntelliJ IDEA**: Right-click on the test class and select "Run" (should auto-detect JUnit 5)
2. **Eclipse**: Install the JUnit 5 support from Eclipse Marketplace if not already present
3. **VS Code**: Ensure Java Test Runner extension is installed and updated

## Benefits of JUnit 5

- **Modern Architecture**: Modular platform design with clear separation between the programming model and test execution
- **Better IDE Support**: Enhanced support in modern IDEs and build tools
- **Rich Assertions**: More expressive assertion methods
- **Extension Model**: Powerful extension mechanism for custom test behaviors
- **Active Development**: JUnit 5 is actively maintained with regular updates

## Support

If you encounter issues during migration:

1. Check the [Cucumber JUnit Platform Engine documentation](https://github.com/cucumber/cucumber-jvm/tree/main/cucumber-junit-platform-engine)
2. Review the [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
3. Open an issue on the Tzatziki GitHub repository

## Summary

This migration updates Tzatziki to use modern JUnit 5 infrastructure while maintaining all existing Cucumber functionality. The main changes are in how you configure test runners - your actual Cucumber feature files and step definitions remain unchanged.
