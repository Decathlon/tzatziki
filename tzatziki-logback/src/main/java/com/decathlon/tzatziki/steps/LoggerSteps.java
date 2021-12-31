package com.decathlon.tzatziki.steps;

import ch.qos.logback.classic.Level;
import com.decathlon.tzatziki.utils.Asserts;
import com.decathlon.tzatziki.utils.Comparison;
import com.decathlon.tzatziki.utils.Guard;
import com.decathlon.tzatziki.utils.Mapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.semla.logging.ListAppender;
import io.semla.logging.Logging;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.encoder.LogstashEncoder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.decathlon.tzatziki.utils.Asserts.awaitUntilAsserted;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Patterns.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Slf4j
public class LoggerSteps {

    static {
        DynamicTransformers.register(Level.class, Level::toLevel);
    }

    private static final String ALL_LEVEL = "(ALL|TRACE|DEBUG|INFO|WARN|ERROR|OFF)";
    private static final String OUTPUT_LEVEL = "(TRACE|DEBUG|INFO|WARN|ERROR)";
    private static final Level DEFAULT_LEVEL = Level.ERROR;
    private static boolean shouldReinstall = true;

    private static ListAppender listAppender = new ListAppender();
    private final ObjectSteps objects;
    private Level level = DEFAULT_LEVEL;
    private final Map<String, Level> loggers = new LinkedHashMap<>();

    public LoggerSteps(ObjectSteps objects) {
        this.objects = objects;
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
        guard.in(objects, () -> await()
                .during(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(listAppender.logLines()).isEmpty())
        );
    }

    @Then(THAT + GUARD + "the logs are formatted in json$")
    public void the_logs_are_formatted_in_json(Guard guard) {
        guard.in(objects, () -> reinstall(true));
    }

    @Then(THAT + GUARD + "the logs" + Comparison.IS_COMPARED_TO + ":$")
    public void the_logs_contain(Guard guard, Comparison comparison, String sourceValue) {
        guard.in(objects, () -> awaitUntilAsserted(() -> comparison.compare(
                listAppender.logLines(),
                Mapper.readAsAListOf(objects.resolve(sourceValue), String.class)
        )));
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

    private void reinstall(boolean useLogStashEncoder) {
        listAppender = new ListAppender();
        Logging.Configurator configurator = Logging.withLogLevel(this.level)
                .withAppender(listAppender)
                .withPattern("%d [%-5level] [%thread] %logger{5} - %message%n");
        loggers.forEach(configurator::withAppenderLevel);
        configurator.setup();
        if (useLogStashEncoder) { //overwrite the default encoder initialized in the configurator's setup method
            LogstashEncoder encoder = new LogstashEncoder();
            encoder.start();
            listAppender.setEncoder(encoder);
        }
    }
}
