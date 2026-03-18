# User Provided Header
Tzatziki Logback module reference.
- LoggerSteps.java defines @Given/@When/@Then patterns for log assertion, log level management, and log output verification.
- .feature files demonstrate valid logging step usage.


# Directory Structure
```
tzatziki-logback/
  src/
    main/
      java/
        com/
          decathlon/
            tzatziki/
              steps/
                LoggerSteps.java
    test/
      resources/
        com/
          decathlon/
            tzatziki/
              steps/
                logger.feature
```

# Files

## File: tzatziki-logback/src/main/java/com/decathlon/tzatziki/steps/LoggerSteps.java
```java
package com.decathlon.tzatziki.steps;

import ch.qos.logback.classic.Level;
import com.decathlon.tzatziki.logging.ListAppender;
import com.decathlon.tzatziki.logging.Logging;
import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.encoder.LogstashEncoder;
import org.assertj.core.api.Condition;
import org.assertj.core.api.ListAssert;

import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;

import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SuppressWarnings("java:S100") // Allow method names with underscores for BDD steps
public class LoggerSteps {

    static {
        DynamicTransformers.register(Level.class, Level::toLevel);
    }

    private static final String ALL_LEVEL = "(ALL|TRACE|DEBUG|INFO|WARN|ERROR|OFF)";
    private static final String OUTPUT_LEVEL = "(TRACE|DEBUG|INFO|WARN|ERROR)";
    private static Level DEFAULT_LEVEL = Level.ERROR;
    private static String DEFAULT_PATTERN = "%d [%-5level] [%thread] %logger{5} - %message%n";
    private static final Map<String, Level> DEFAULT_LOGGERS = new LinkedHashMap<>();
    private static boolean shouldReinstall = true;

    private static ListAppender listAppender = new ListAppender();
    private final ObjectSteps objects;
    private Level level = DEFAULT_LEVEL;
    private final Map<String, Level> loggers = new LinkedHashMap<>();

    public LoggerSteps(ObjectSteps objects) {
        this.objects = objects;
    }

    public static void setDefaultLevel(Level level) {
        DEFAULT_LEVEL = level;
    }

    public static void setLoggerlevel(String logger, Level level) {
        DEFAULT_LOGGERS.put(logger, level);
    }

    public static void setDefaultPattern(String defaultPattern) {
        DEFAULT_PATTERN = defaultPattern;
    }

    @Before(order = 0)
    public void before() {
        if (shouldReinstall) {
            shouldReinstall = false;
            reinstall(false);
        } else {
            listAppender.logLines().clear();
        }
    }

    @Given(THAT + GUARD + "a " + TYPE_OR_PACKAGE + " logger set to " + ALL_LEVEL + "$")
    public void a_given_logger_set_to(Guard guard, String logger, Level level) {
        guard.in(objects, () -> {
            shouldReinstall = true;
            if (logger.equals("root")) {
                this.level = level;
            } else {
                loggers.put(logger, level);
            }
            reinstall(false);
        });
    }

    @When(THAT + GUARD + A_USER + "logs? as " + OUTPUT_LEVEL + ":$")
    public void log(Guard guard, Level level, Object value) {
        guard.in(objects, () -> {
            switch (level.levelStr) {
                case "TRACE" -> log.trace(objects.resolve(value));
                case "DEBUG" -> log.debug(objects.resolve(value));
                case "INFO" -> log.info(objects.resolve(value));
                case "WARN" -> log.warn(objects.resolve(value));
                case "ERROR" -> log.error(objects.resolve(value));
                default -> throw new IllegalStateException("Unexpected value: " + level);
            }
        });
    }

    @Given(THAT + GUARD + A_USER + "empt(?:y|ies) the logs$")
    public void empty_the_logs(Guard guard) {
        guard.in(objects, () -> listAppender.logLines().clear());
    }

    @Then(THAT + GUARD + "the logs are empty$")
    public void the_logs_are_empty(Guard guard) {
        guard.in(objects, () -> assertThat(List.of(listAppender.logLines().toArray(new String[0]))).isEmpty());
    }

    @Then(THAT + GUARD + "the logs are formatted in json$")
    public void the_logs_are_formatted_in_json(Guard guard) {
        guard.in(objects, () -> reinstall(true));
    }

    @Then(THAT + GUARD + "the logs" + Comparison.IS_COMPARED_TO + ":$")
    public void the_logs_contain(Guard guard, Comparison comparison, String sourceValue) {
        guard.in(objects, () -> comparison.compare(
                List.of(listAppender.logLines().toArray(new String[0])),
                Mapper.readAsAListOf(objects.resolve(sourceValue), String.class)
        ));
    }

    @Then(THAT + GUARD + "the logs contains?(?: " + VERIFICATION + ")? " + COUNT_OR_VARIABLE + " lines? (?:==|equal to) " + QUOTED_CONTENT + "$")
    public void the_logs_contain(Guard guard, String verification, String countAsString, String content) {
        guard.in(objects, () -> {
            int expectedNbCalls = objects.getCount(countAsString);
            String expected = objects.resolve(content);
            Condition<String> expectedCondition = new Condition<>(s -> {
                try {
                    Comparison.EQUALS.compare(s, expected);
                    return true;
                } catch (Throwable t) {
                    return false;
                }
            }, "match");

            ListAssert<String> assertStump = assertThat(List.of(listAppender.logLines().toArray(new String[0])));

            Optional.ofNullable(verification)
                    .<IntFunction<ListAssert<String>>>map(v ->
                            switch (v) {
                                case "at least" -> i -> assertStump.areAtLeast(i, expectedCondition);
                                case "at most" -> i -> assertStump.areAtMost(i, expectedCondition);
                                default -> i -> assertStump.areExactly(i, expectedCondition);
                            })
                    .orElse(i -> assertStump.areExactly(i, expectedCondition))
                    .apply(expectedNbCalls);
        });
    }

    private void reinstall(boolean useLogStashEncoder) {
        listAppender = new ListAppender();
        listAppender.setOutputStream(OutputStream.nullOutputStream());
        Logging.Configurator configurator = Logging.withLogLevel(this.level)
                .withAppender(listAppender)
                .withPattern(DEFAULT_PATTERN);
        DEFAULT_LOGGERS.forEach(configurator::withAppenderLevel);
        loggers.forEach(configurator::withAppenderLevel);
        configurator.setup();
        if (useLogStashEncoder) { //overwrite the default encoder initialized in the configurator's setup method
            LogstashEncoder encoder = new LogstashEncoder();
            encoder.start();
            listAppender.setEncoder(encoder);
        }
    }
}
```

## File: tzatziki-logback/src/test/resources/com/decathlon/tzatziki/steps/logger.feature
```
Feature: to interact with the logger

  Scenario: we can set the log level to OFF
    Given a root logger set to OFF
    When we log as INFO:
      """
      some log lines
      """
    Then the logs are empty

  Scenario Template: we can assert the content of the logs
    Given a root logger set to <level>
    When something logs as ERROR:
      """
      some log lines that should be there
      """
    Then if <level> == INFO => the logs contain:
      """
      - ?e .* some [^ ]+ lines that should be there
      """
    But if <level> == OFF => the logs are empty

    Examples:
      | level |
      | INFO  |
      | OFF   |

  Scenario: we can assert that the logs do not contain something
    Given a root logger set to INFO
    When we log as INFO:
      """
      some log lines that should be there
      """
    Then it is not true that the logs contain:
      """
      - ?e .* some [^ ]+ lines that should not be there
      """

  Scenario: we can set the log level of a specific class
    Given a com.decathlon.tzatziki.steps logger set to DEBUG
    When we log as DEBUG:
      """
      some lines
      """
    Then the logs contain:
      """
      - ?e .* some lines
      """

  Scenario Template: we can assert the content of the logs (log in JSON)
    Given a root logger set to <level>
    And the logs are formatted in json
    When something logs as ERROR:
      """
      some log lines that should be there
      """
    Then if <level> == INFO => the logs contain:
      """
      - ?e  *.*"message":"some [^ ]+ lines that should be there","logger_name":"com.decathlon.tzatziki.steps.LoggerSteps","thread_name":"main","level":"ERROR".*
      """
    But if <level> == OFF => the logs are empty

    Examples:
      | level |
      | INFO  |
      | OFF   |

  Scenario: we can assert that lines are in a given order
    Given a root logger set to INFO
    When we log as INFO:
      """
      this is the first line
      """

    And we log as INFO:
      """
      this is the second line
      """

    Then the logs contain:
      """
      - ?e .*this is the second line.*
      - ?e .*this is the first line.*
      """

    And it is not true that the logs contain in order:
      """
      - ?e .*this is the second line.*
      - ?e .*this is the first line.*
      """

    And the logs contain in order:
      """
      - ?e .*this is the first line.*
      - ?e .*this is the second line.*
      """
  Scenario: we can assert that lines match multiple times
    Given a root logger set to INFO
    When we log as INFO:
      """
      this is the first line
      """

    And we log as INFO:
      """
      this is the first line
      """

    Then the logs contains at least 1 line equal to "?e .*this is the first line.*"

    And the logs contains exactly 2 lines equal to "?e .*this is the first line.*"

    And the logs contains 2 lines == "?e .*this is the first line.*"

    And it is not true that the logs contains at most 1 line equal to "?e .*this is the first line.*"


  Scenario: there shouldn't be any "within" implicit guard in logger response assertions
    When after 500ms something logs as ERROR:
      """
      some log lines that should be there
      """
    Then it is not true that the logs contain:
      """
      - ?e .* some [^ ]+ lines that should be there
      """
    But within 600ms the logs contain:
      """
      - ?e .* some [^ ]+ lines that should be there
      """
```
